package org.openelisglobal.middleware;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.analyzer.service.AnalyzerService;
import org.openelisglobal.analyzer.valueholder.Analyzer;
import org.openelisglobal.analyzerimport.service.AnalyzerTestMappingService;
import org.openelisglobal.analyzerimport.valueholder.AnalyzerTestMapping;
import org.openelisglobal.analyzerresults.service.AnalyzerResultsService;
import org.openelisglobal.analyzerresults.valueholder.AnalyzerResults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiddlewareIngestionService {

    private static final DateTimeFormatter[] SUPPORTED_DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"), DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss") };

    private final ObjectMapper objectMapper;
    private final AnalyzerService analyzerService;
    private final AnalyzerTestMappingService analyzerTestMappingService;
    private final AnalyzerResultsService analyzerResultsService;
    private final String expectedApiKey;
    private final String systemUserId;

    public MiddlewareIngestionService(ObjectMapper objectMapper, AnalyzerService analyzerService,
            AnalyzerTestMappingService analyzerTestMappingService, AnalyzerResultsService analyzerResultsService,
            @Value("${org.openelisglobal.middleware.api-key:}") String expectedApiKey,
            @Value("${org.openelisglobal.middleware.system-user-id:1}") String systemUserId) {
        this.objectMapper = objectMapper;
        this.analyzerService = analyzerService;
        this.analyzerTestMappingService = analyzerTestMappingService;
        this.analyzerResultsService = analyzerResultsService;
        this.expectedApiKey = expectedApiKey;
        this.systemUserId = systemUserId;
    }

    public boolean isApiKeyValid(String providedApiKey) {
        if (!isApiKeyConfigured()) {
            return false;
        }
        return expectedApiKey.equals(providedApiKey);
    }

    public boolean isApiKeyConfigured() {
        return !GenericValidator.isBlankOrNull(expectedApiKey);
    }

    @Transactional
    public MiddlewareReceiveResultResponse ingest(String payload) {
        List<AnalyzerResultDTO> incomingResults = parsePayload(payload);
        if (incomingResults.isEmpty()) {
            throw new IllegalArgumentException("No analyzer results found in payload");
        }

        Map<String, Map<String, String>> analyzerToTestMappings = new HashMap<>();
        List<AnalyzerResults> resultsToPersist = new ArrayList<>();
        int readOnlyCount = 0;

        for (AnalyzerResultDTO dto : incomingResults) {
            AnalyzerResults mappedResult = mapToAnalyzerResult(dto, analyzerToTestMappings);
            if (mappedResult.isReadOnly()) {
                readOnlyCount++;
            }
            resultsToPersist.add(mappedResult);
        }

        analyzerResultsService.insertAnalyzerResults(resultsToPersist, systemUserId);
        return MiddlewareReceiveResultResponse.success("Received", incomingResults.size(), resultsToPersist.size(),
                readOnlyCount);
    }

    private List<AnalyzerResultDTO> parsePayload(String payload) {
        if (GenericValidator.isBlankOrNull(payload)) {
            throw new IllegalArgumentException("Payload is empty");
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            List<AnalyzerResultDTO> parsed = new ArrayList<>();
            if (jsonNode.isArray()) {
                for (JsonNode node : jsonNode) {
                    parsed.add(objectMapper.treeToValue(node, AnalyzerResultDTO.class));
                }
            } else if (jsonNode.isObject()) {
                parsed.add(objectMapper.treeToValue(jsonNode, AnalyzerResultDTO.class));
            } else {
                throw new IllegalArgumentException("Payload must be a JSON object or array");
            }
            return parsed;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }

    private AnalyzerResults mapToAnalyzerResult(AnalyzerResultDTO dto,
            Map<String, Map<String, String>> analyzerToTestMappings) {
        validateRequiredFields(dto);

        String analyzerId = resolveAnalyzerId(dto);
        String analyzerTestName = resolveAnalyzerTestName(dto);
        Map<String, String> mappings = analyzerToTestMappings.computeIfAbsent(analyzerId, this::loadMappingsForAnalyzer);
        String mappedTestId = mappings.get(normalizeKey(analyzerTestName));

        AnalyzerResults result = new AnalyzerResults();
        result.setAnalyzerId(analyzerId);
        result.setAccessionNumber(dto.getAccessionNumber());
        result.setTestName(analyzerTestName);
        result.setResult(dto.getResultValue());
        result.setUnits(dto.getUnits());
        result.setCompleteDate(resolveCompleteDate(dto.getResultDateTime()));
        result.setResultType("N");
        result.setIsControl(dto.getAccessionNumber().trim().contains(" "));

        if (mappedTestId != null) {
            result.setTestId(mappedTestId);
            result.setReadOnly(false);
        } else {
            result.setTestId(null);
            result.setReadOnly(true);
        }

        return result;
    }

    private void validateRequiredFields(AnalyzerResultDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("One result entry is null");
        }
        if (GenericValidator.isBlankOrNull(dto.getAccessionNumber())) {
            throw new IllegalArgumentException("Field accessionNumber is required");
        }
        if (GenericValidator.isBlankOrNull(resolveAnalyzerTestName(dto))) {
            throw new IllegalArgumentException("Field testCode or testName is required");
        }
        if (GenericValidator.isBlankOrNull(dto.getResultValue())) {
            throw new IllegalArgumentException("Field resultValue is required");
        }
    }

    private String resolveAnalyzerId(AnalyzerResultDTO dto) {
        if (!GenericValidator.isBlankOrNull(dto.getAnalyzerId())) {
            return dto.getAnalyzerId();
        }
        if (GenericValidator.isBlankOrNull(dto.getInstrumentName())) {
            throw new IllegalArgumentException("Field instrumentName or analyzerId is required");
        }

        Analyzer analyzer = analyzerService.getAnalyzerByName(dto.getInstrumentName());
        if (analyzer == null || GenericValidator.isBlankOrNull(analyzer.getId())) {
            throw new IllegalArgumentException("Analyzer not found for instrumentName: " + dto.getInstrumentName());
        }
        return analyzer.getId();
    }

    private String resolveAnalyzerTestName(AnalyzerResultDTO dto) {
        if (!GenericValidator.isBlankOrNull(dto.getTestCode())) {
            return dto.getTestCode().trim();
        }
        if (!GenericValidator.isBlankOrNull(dto.getTestName())) {
            return dto.getTestName().trim();
        }
        return null;
    }

    private Map<String, String> loadMappingsForAnalyzer(String analyzerId) {
        Map<String, String> mappings = new HashMap<>();
        List<AnalyzerTestMapping> allMappings = analyzerTestMappingService.getAllForAnalyzer(analyzerId);
        if (allMappings == null) {
            return mappings;
        }
        for (AnalyzerTestMapping mapping : allMappings) {
            if (!GenericValidator.isBlankOrNull(mapping.getAnalyzerTestName())
                    && !GenericValidator.isBlankOrNull(mapping.getTestId())) {
                mappings.put(normalizeKey(mapping.getAnalyzerTestName()), mapping.getTestId());
            }
        }
        return mappings;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private Timestamp resolveCompleteDate(String resultDateTime) {
        if (GenericValidator.isBlankOrNull(resultDateTime)) {
            return new Timestamp(System.currentTimeMillis());
        }

        try {
            return Timestamp.from(Instant.parse(resultDateTime));
        } catch (DateTimeParseException e) {
            // Continue trying other date formats.
        }

        try {
            return Timestamp.from(OffsetDateTime.parse(resultDateTime, DateTimeFormatter.ISO_DATE_TIME).toInstant());
        } catch (DateTimeParseException e) {
            // Continue trying other date formats.
        }

        try {
            return Timestamp.valueOf(LocalDateTime.parse(resultDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } catch (DateTimeParseException e) {
            // Continue trying other date formats.
        }

        for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATS) {
            try {
                return Timestamp.valueOf(LocalDateTime.parse(resultDateTime, formatter));
            } catch (DateTimeParseException e) {
                // Keep trying other supported formats.
            }
        }

        throw new IllegalArgumentException("Unsupported resultDateTime format: " + resultDateTime);
    }
}
