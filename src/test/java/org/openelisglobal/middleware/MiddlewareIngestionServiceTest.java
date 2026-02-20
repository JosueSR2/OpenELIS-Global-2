package org.openelisglobal.middleware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.analyzer.service.AnalyzerService;
import org.openelisglobal.analyzer.valueholder.Analyzer;
import org.openelisglobal.analyzerimport.service.AnalyzerTestMappingService;
import org.openelisglobal.analyzerimport.valueholder.AnalyzerTestMapping;
import org.openelisglobal.analyzerresults.service.AnalyzerResultsService;
import org.openelisglobal.analyzerresults.valueholder.AnalyzerResults;

@RunWith(MockitoJUnitRunner.class)
public class MiddlewareIngestionServiceTest {

    @Mock
    private AnalyzerService analyzerService;

    @Mock
    private AnalyzerTestMappingService analyzerTestMappingService;

    @Mock
    private AnalyzerResultsService analyzerResultsService;

    private MiddlewareIngestionService ingestionService;

    @Before
    public void setUp() {
        ingestionService = new MiddlewareIngestionService(new ObjectMapper(), analyzerService, analyzerTestMappingService,
                analyzerResultsService, "expected-key", "1");
    }

    @Test
    public void shouldValidateApiKeyWhenConfigured() {
        assertTrue(ingestionService.isApiKeyValid("expected-key"));
        assertFalse(ingestionService.isApiKeyValid("bad-key"));
    }

    @Test
    public void shouldRejectRequestsWhenApiKeyIsNotConfigured() {
        MiddlewareIngestionService noKeyService = new MiddlewareIngestionService(new ObjectMapper(), analyzerService,
                analyzerTestMappingService, analyzerResultsService, "", "1");

        assertFalse(noKeyService.isApiKeyValid(null));
        assertFalse(noKeyService.isApiKeyValid("any-value"));
    }

    @Test
    public void shouldPersistMappedResult() {
        Analyzer analyzer = new Analyzer();
        analyzer.setId("7");
        when(analyzerService.getAnalyzerByName("Cobas-1")).thenReturn(analyzer);

        AnalyzerTestMapping mapping = new AnalyzerTestMapping();
        mapping.setAnalyzerId("7");
        mapping.setAnalyzerTestName("GLU");
        mapping.setTestId("99");
        when(analyzerTestMappingService.getAllForAnalyzer("7")).thenReturn(Arrays.asList(mapping));

        MiddlewareReceiveResultResponse response = ingestionService.ingest(
                "{\"instrumentName\":\"Cobas-1\",\"accessionNumber\":\"A-100\",\"testCode\":\"GLU\",\"resultValue\":\"5.6\",\"units\":\"mmol/L\"}");

        assertTrue(response.isSuccess());
        assertEquals(1, response.getReceivedCount());
        assertEquals(1, response.getPersistedCount());
        assertEquals(0, response.getReadOnlyCount());

        ArgumentCaptor<List> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(analyzerResultsService).insertAnalyzerResults(listCaptor.capture(), eq("1"));
        AnalyzerResults persisted = (AnalyzerResults) listCaptor.getValue().get(0);
        assertEquals("7", persisted.getAnalyzerId());
        assertEquals("A-100", persisted.getAccessionNumber());
        assertEquals("GLU", persisted.getTestName());
        assertEquals("99", persisted.getTestId());
        assertFalse(persisted.isReadOnly());
    }

    @Test
    public void shouldMarkResultReadOnlyWhenTestMappingMissing() {
        Analyzer analyzer = new Analyzer();
        analyzer.setId("8");
        when(analyzerService.getAnalyzerByName("Sysmex-1")).thenReturn(analyzer);
        when(analyzerTestMappingService.getAllForAnalyzer("8")).thenReturn(Collections.emptyList());

        MiddlewareReceiveResultResponse response = ingestionService.ingest(
                "{\"instrumentName\":\"Sysmex-1\",\"accessionNumber\":\"A-200\",\"testCode\":\"WBC\",\"resultValue\":\"4.2\"}");

        assertTrue(response.isSuccess());
        assertEquals(1, response.getReadOnlyCount());

        ArgumentCaptor<List> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(analyzerResultsService).insertAnalyzerResults(listCaptor.capture(), eq("1"));
        AnalyzerResults persisted = (AnalyzerResults) listCaptor.getValue().get(0);
        assertTrue(persisted.isReadOnly());
        assertNull(persisted.getTestId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailWhenAnalyzerCannotBeResolved() {
        when(analyzerService.getAnalyzerByName("Unknown-Analyzer")).thenReturn(null);

        ingestionService.ingest(
                "{\"instrumentName\":\"Unknown-Analyzer\",\"accessionNumber\":\"A-300\",\"testCode\":\"GLU\",\"resultValue\":\"5.0\"}");
    }
}
