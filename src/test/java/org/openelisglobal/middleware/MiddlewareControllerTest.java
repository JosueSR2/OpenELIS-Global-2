package org.openelisglobal.middleware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RunWith(MockitoJUnitRunner.class)
public class MiddlewareControllerTest {

    @Mock
    private MiddlewareService middlewareService;

    @Mock
    private MiddlewareIngestionService ingestionService;

    private MiddlewareController controller;

    @Before
    public void setUp() {
        controller = new MiddlewareController(middlewareService, ingestionService);
    }

    @Test
    public void shouldReturnUnauthorizedWhenApiKeyIsInvalid() {
        when(ingestionService.isApiKeyConfigured()).thenReturn(true);
        when(ingestionService.isApiKeyValid("bad-key")).thenReturn(false);

        ResponseEntity<MiddlewareReceiveResultResponse> response = controller.receiveResult("{}", "bad-key");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    public void shouldReturnBadRequestForInvalidPayload() {
        when(ingestionService.isApiKeyConfigured()).thenReturn(true);
        when(ingestionService.isApiKeyValid("good-key")).thenReturn(true);
        when(ingestionService.ingest("{}")).thenThrow(new IllegalArgumentException("Invalid payload"));

        ResponseEntity<MiddlewareReceiveResultResponse> response = controller.receiveResult("{}", "good-key");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid payload", response.getBody().getMessage());
    }

    @Test
    public void shouldReturnOkForValidPayload() {
        when(ingestionService.isApiKeyConfigured()).thenReturn(true);
        when(ingestionService.isApiKeyValid("good-key")).thenReturn(true);
        when(ingestionService.ingest("{}"))
                .thenReturn(MiddlewareReceiveResultResponse.success("Received", 1, 1, 0));

        ResponseEntity<MiddlewareReceiveResultResponse> response = controller.receiveResult("{}", "good-key");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getPersistedCount());
    }

    @Test
    public void shouldReturnServiceUnavailableWhenApiKeyNotConfigured() {
        when(ingestionService.isApiKeyConfigured()).thenReturn(false);

        ResponseEntity<MiddlewareReceiveResultResponse> response = controller.receiveResult("{}", "any-key");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Middleware API key is not configured", response.getBody().getMessage());
    }
}
