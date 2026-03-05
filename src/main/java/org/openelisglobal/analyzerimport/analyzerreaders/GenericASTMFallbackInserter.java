package org.openelisglobal.analyzerimport.analyzerreaders;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.analyzer.valueholder.Analyzer;
import org.openelisglobal.analyzerimport.util.AnalyzerTestNameCache;
import org.openelisglobal.analyzerimport.util.MappedTestName;
import org.openelisglobal.analyzerresults.valueholder.AnalyzerResults;

/**
 * Minimal ASTM fallback inserter used when a legacy plugin matches but cannot
 * persist (for example, a partially implemented plugin).
 */
public class GenericASTMFallbackInserter extends AnalyzerLineInserter {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0002\\u0003\\u0004\\u0005\\u0006\\u0015\\u0017]");
    private static final Pattern FRAME_PREFIX = Pattern.compile("^(\\d+)([A-Z])\\|");
    private final Analyzer analyzer;
    private String error;

    public GenericASTMFallbackInserter(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public boolean insert(List<String> lines, String currentUserId) {
        error = null;

        if (analyzer == null || analyzer.getId() == null) {
            error = "ASTM fallback insert failed: analyzer could not be identified";
            return false;
        }

        if (lines == null || lines.isEmpty()) {
            error = "ASTM fallback insert failed: message is empty";
            return false;
        }

        List<AnalyzerResults> results = extractResults(lines);
        if (results.isEmpty()) {
            error = "ASTM fallback insert failed: no result records were parsed";
            return false;
        }

        boolean persisted = persistImport(currentUserId, results);
        if (!persisted) {
            error = "ASTM fallback insert failed while writing parsed results";
        }
        return persisted;
    }

    private List<AnalyzerResults> extractResults(List<String> lines) {
        List<AnalyzerResults> results = new ArrayList<>();
        String currentAccession = null;
        Timestamp now = new Timestamp(System.currentTimeMillis());

        for (String rawLine : lines) {
            String line = normalizeLine(rawLine);
            if (StringUtils.isBlank(line)) {
                continue;
            }

            String[] fields = line.split("\\|", -1);
            if (fields.length == 0) {
                continue;
            }

            String segment = fields[0];
            if ("O".equals(segment)) {
                String accession = extractAccession(fields);
                if (StringUtils.isNotBlank(accession)) {
                    currentAccession = accession;
                }
                continue;
            }

            if (!"R".equals(segment) || StringUtils.isBlank(currentAccession)) {
                continue;
            }

            String analyzerTestCode = extractTestCode(fields.length > 2 ? fields[2] : null);
            String resultValue = cleanField(fields.length > 3 ? fields[3] : null);
            String units = cleanField(fields.length > 4 ? fields[4] : null);

            if (StringUtils.isBlank(analyzerTestCode) || StringUtils.isBlank(resultValue)) {
                continue;
            }

            results.add(buildResult(currentAccession, analyzerTestCode, resultValue, units, now));
        }

        return results;
    }

    private AnalyzerResults buildResult(String accessionNumber, String analyzerTestCode, String resultValue, String units,
            Timestamp completedAt) {
        AnalyzerResults analyzerResult = new AnalyzerResults();

        MappedTestName mapped = AnalyzerTestNameCache.getInstance().getMappedTest(analyzer.getName(), analyzerTestCode);
        if (mapped == null) {
            mapped = AnalyzerTestNameCache.getInstance().getEmptyMappedTestName(analyzer.getName(), analyzerTestCode);
        }

        analyzerResult.setAnalyzerId(analyzer.getId());
        analyzerResult.setAccessionNumber(accessionNumber);
        analyzerResult.setTestName(StringUtils.defaultIfBlank(mapped.getOpenElisTestName(), analyzerTestCode));
        analyzerResult.setTestId(StringUtils.defaultIfBlank(mapped.getTestId(), "-1"));
        analyzerResult.setResult(resultValue);
        analyzerResult.setUnits(units);
        analyzerResult.setResultType(isNumericValue(resultValue) ? "N" : "A");
        analyzerResult.setCompleteDate(completedAt);
        analyzerResult.setIsControl(isControlAccession(accessionNumber));
        return analyzerResult;
    }

    private boolean isNumericValue(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.matches("^[+-]?[0-9]+([.,][0-9]+)?$");
    }

    private boolean isControlAccession(String accession) {
        if (StringUtils.isBlank(accession)) {
            return false;
        }
        String normalized = accession.toUpperCase();
        return normalized.startsWith("QC") || normalized.contains("CONTROL");
    }

    private String extractAccession(String[] fields) {
        String firstCandidate = extractPrimaryToken(fields.length > 2 ? fields[2] : null);
        if (StringUtils.isNotBlank(firstCandidate)) {
            return firstCandidate;
        }
        return extractPrimaryToken(fields.length > 3 ? fields[3] : null);
    }

    private String extractTestCode(String testField) {
        String cleaned = cleanField(testField);
        if (StringUtils.isBlank(cleaned)) {
            return null;
        }
        String[] parts = cleaned.split("\\^", -1);
        for (int i = parts.length - 1; i >= 0; i--) {
            if (StringUtils.isNotBlank(parts[i])) {
                return parts[i].trim();
            }
        }
        return cleaned;
    }

    private String extractPrimaryToken(String field) {
        String cleaned = cleanField(field);
        if (StringUtils.isBlank(cleaned)) {
            return null;
        }
        String[] parts = cleaned.split("\\^", -1);
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                return part.trim();
            }
        }
        return cleaned;
    }

    private String normalizeLine(String rawLine) {
        if (rawLine == null) {
            return "";
        }

        String line = CONTROL_CHARS.matcher(rawLine).replaceAll("");
        int etxIndex = line.indexOf('\u0003');
        if (etxIndex >= 0) {
            line = line.substring(0, etxIndex);
        }
        line = FRAME_PREFIX.matcher(line).replaceFirst("$2|");
        return line.trim();
    }

    private String cleanField(String value) {
        if (value == null) {
            return null;
        }
        return CONTROL_CHARS.matcher(value).replaceAll("").trim();
    }

    @Override
    public String getError() {
        return error;
    }
}
