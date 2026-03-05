/**
 * The contents of this file are subject to the Mozilla Public License Version 1.1 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.mozilla.org/MPL/
 *
 * <p>Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * <p>The Original Code is OpenELIS code.
 *
 * <p>Copyright (C) The Minnesota Department of Health. All Rights Reserved.
 *
 * <p>Contributor(s): CIRG, University of Washington, Seattle WA.
 */
package org.openelisglobal.analyzerimport.analyzerreaders;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.analyzer.service.AnalyzerService;
import org.openelisglobal.analyzer.service.HL7MessageService;
import org.openelisglobal.analyzer.valueholder.Analyzer;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.services.PluginAnalyzerService;
import org.openelisglobal.plugin.AnalyzerImporterPlugin;
import org.openelisglobal.spring.util.SpringContext;

/**
 * AnalyzerReader for HL7 v2.x ORU^R01 result messages.
 *
 * <p>
 * HL7 handling is plugin-only (e.g. GenericHL7). Parses message with
 * HL7MessageService for validation and segment lines, then delegates to the
 * matching plugin's inserter. No built-in/legacy HL7 inserter fallback.
 */
public class HL7AnalyzerReader extends AnalyzerReader {

    private List<String> lines;
    private String error;
    private Analyzer configuredAnalyzer;

    @Override
    public boolean readStream(InputStream stream) {
        error = null;
        lines = null;
        configuredAnalyzer = null;
        try {
            String raw = IOUtils.toString(stream, StandardCharsets.UTF_8);
            if (StringUtils.isBlank(raw)) {
                error = "Empty HL7 message";
                return false;
            }
            HL7MessageService svc = SpringContext.getBean(HL7MessageService.class);
            svc.parseOruR01(raw);
            lines = svc.toSegmentLines(raw);
            return !lines.isEmpty();
        } catch (HL7MessageService.HL7ParseException e) {
            error = "HL7 parse error: " + e.getMessage();
            LogEvent.logError(e);
            return false;
        } catch (Exception e) {
            error = "Failed to read HL7 stream: " + e.getMessage();
            LogEvent.logError(e);
            return false;
        }
    }

    @Override
    public boolean insertAnalyzerData(String systemUserId) {
        if (lines == null || lines.isEmpty()) {
            error = "No HL7 message loaded";
            return false;
        }

        PluginAnalyzerService pluginService = SpringContext.getBean(PluginAnalyzerService.class);
        List<AnalyzerImporterPlugin> plugins = choosePluginOrder(pluginService);
        String msh3Identifier = parseMsh3(lines);
        AnalyzerImporterPlugin configuredPlugin = resolvePluginByIdentifier(pluginService, msh3Identifier);
        if (!StringUtils.isBlank(msh3Identifier) && configuredPlugin == null && configuredAnalyzer == null) {
            error = "No HL7 analyzer configuration matched MSH-3 identifier '" + msh3Identifier + "'";
            LogEvent.logError(getClass().getSimpleName(), "insertAnalyzerData", error);
            return false;
        }
        if (configuredPlugin == null && configuredAnalyzer != null) {
            return tryBuiltInHl7Insert(systemUserId);
        }
        if (configuredPlugin != null) {
            try {
                AnalyzerLineInserter inserter = configuredPlugin.getAnalyzerLineInserter();
                if (inserter != null) {
                    if (!isLikelyHl7Plugin(configuredPlugin)) {
                        return tryBuiltInHl7Insert(systemUserId);
                    }
                    boolean success = inserter.insert(lines, systemUserId);
                    if (!success) {
                        error = inserter.getError();
                        if (configuredAnalyzer != null
                                && error != null
                                && error.toLowerCase().contains("unable to write to database")
                                && tryBuiltInHl7Insert(systemUserId)) {
                            return true;
                        }
                        LogEvent.logError(getClass().getSimpleName(), "insertAnalyzerData", error);
                    }
                    return success;
                }
                error = "Configured HL7 plugin has no analyzer line inserter";
                LogEvent.logError(getClass().getSimpleName(), "insertAnalyzerData", error);
                return false;
            } catch (RuntimeException e) {
                error = "Configured HL7 plugin failed: " + e.getMessage();
                LogEvent.logWarn(getClass().getSimpleName(), "insertAnalyzerData", error);
                return false;
            }
        }

        boolean pluginMatched = false;
        for (AnalyzerImporterPlugin plugin : plugins) {
            if (configuredPlugin != null && plugin == configuredPlugin) {
                continue;
            }

            boolean matched;
            try {
                matched = plugin.isTargetAnalyzer(lines);
            } catch (RuntimeException e) {
                LogEvent.logWarn(getClass().getSimpleName(), "insertAnalyzerData",
                        "Plugin detection failed for " + plugin.getClass().getSimpleName() + ": " + e.getMessage());
                continue;
            }

            if (!matched) {
                continue;
            }

            pluginMatched = true;
            try {
                AnalyzerLineInserter inserter = plugin.getAnalyzerLineInserter();
                if (inserter != null) {
                    boolean success = inserter.insert(lines, systemUserId);
                    if (!success) {
                        error = inserter.getError();
                        LogEvent.logError(getClass().getSimpleName(), "insertAnalyzerData", error);
                    }
                    return success;
                }
            } catch (RuntimeException e) {
                error = "Plugin " + plugin.getClass().getSimpleName() + " matched but failed: " + e.getMessage();
                LogEvent.logWarn(getClass().getSimpleName(), "insertAnalyzerData", error);
            }
        }

        if (!pluginMatched) {
            error = "No HL7 plugin matched this message (e.g. configure GenericHL7 with matching identifier pattern)";
        } else if (StringUtils.isBlank(error)) {
            error = "Matched HL7 plugin has no analyzer line inserter configured";
        }
        LogEvent.logError(getClass().getSimpleName(), "insertAnalyzerData", error);
        return false;
    }

    /**
     * Return the plugin list in default order. (preferGenericPlugin flag has been
     * removed.)
     */
    private List<AnalyzerImporterPlugin> choosePluginOrder(PluginAnalyzerService pluginService) {
        return pluginService.getAnalyzerPlugins();
    }

    private AnalyzerImporterPlugin resolvePluginByIdentifier(PluginAnalyzerService pluginService, String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return null;
        }

        try {
            AnalyzerService analyzerService = SpringContext.getBean(AnalyzerService.class);
            if (analyzerService == null) {
                return null;
            }
            Optional<Analyzer> analyzerOpt = analyzerService.findByIdentifierPatternMatch(identifier);
            if (analyzerOpt.isEmpty() || analyzerOpt.get().getId() == null) {
                return null;
            }
            Analyzer analyzer = analyzerOpt.get();
            configuredAnalyzer = analyzer;

            // Preferred lookup: explicit analyzer-id registration.
            AnalyzerImporterPlugin plugin = pluginService.getPluginByAnalyzerId(analyzer.getId());
            if (plugin != null) {
                return plugin;
            }

            // Fallback: resolve by analyzer_type.plugin_class_name for legacy plugins
            // that are loaded but not registered by analyzer ID.
            if (analyzer.getAnalyzerType() != null && analyzer.getAnalyzerType().getPluginClassName() != null) {
                String pluginClassName = analyzer.getAnalyzerType().getPluginClassName();
                for (AnalyzerImporterPlugin candidate : pluginService.getAnalyzerPlugins()) {
                    if (candidate.getClass().getName().equals(pluginClassName)) {
                        return candidate;
                    }
                }
            }

            LogEvent.logWarn(getClass().getSimpleName(), "resolvePluginByIdentifier",
                    "Analyzer matched by MSH-3 but no loaded plugin was resolvable for analyzer id="
                            + analyzer.getId());
            return null;
        } catch (RuntimeException e) {
            LogEvent.logWarn(getClass().getSimpleName(), "resolvePluginByIdentifier",
                    "Failed to resolve analyzer by MSH-3 identifier: " + e.getMessage());
            return null;
        }
    }

    /** HL7 MSH segment field 3 (sending application). Same as GenericHL7. */
    private String parseMsh3(List<String> lines) {
        if (lines == null) {
            return null;
        }
        for (String line : lines) {
            if (line != null && line.startsWith("MSH|")) {
                String[] fields = line.split("\\|");
                if (fields.length > 2 && !StringUtils.isBlank(fields[2])) {
                    return fields[2].trim();
                }
                break;
            }
        }
        return null;
    }

    @Override
    public String getError() {
        return error;
    }

    private boolean isLikelyHl7Plugin(AnalyzerImporterPlugin plugin) {
        if (plugin == null) {
            return false;
        }
        String className = plugin.getClass().getName().toLowerCase();
        return className.contains("hl7");
    }

    private boolean tryBuiltInHl7Insert(String systemUserId) {
        if (configuredAnalyzer == null || configuredAnalyzer.getId() == null) {
            return false;
        }
        HL7AnalyzerLineInserter fallbackInserter = new HL7AnalyzerLineInserter(configuredAnalyzer);
        boolean success = fallbackInserter.insert(lines, systemUserId);
        if (!success) {
            error = fallbackInserter.getError();
            return false;
        }
        return true;
    }
}
