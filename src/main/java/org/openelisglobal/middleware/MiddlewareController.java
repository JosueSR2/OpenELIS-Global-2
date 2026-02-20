package org.openelisglobal.middleware;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/middleware")
public class MiddlewareController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MiddlewareController.class);

    private final MiddlewareService service;
    private final MiddlewareIngestionService ingestionService;

    public MiddlewareController(MiddlewareService service, MiddlewareIngestionService ingestionService) {
        this.service = service;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/test")

    public ResponseEntity<String> test() throws IOException, InterruptedException {
        return ResponseEntity.ok(service.testConnection());
    }

    @GetMapping("/results")

    public ResponseEntity<List<String>> getResults() throws IOException, InterruptedException {
        return ResponseEntity.ok(service.getResults());
    }

    @PostMapping("/receive-result")
    public ResponseEntity<MiddlewareReceiveResultResponse> receiveResult(@RequestBody String json,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        if (!ingestionService.isApiKeyConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(MiddlewareReceiveResultResponse.error("Middleware API key is not configured"));
        }

        if (!ingestionService.isApiKeyValid(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(MiddlewareReceiveResultResponse.error("Invalid or missing API key"));
        }

        try {
            MiddlewareReceiveResultResponse response = ingestionService.ingest(json);
            LOGGER.info("Processed middleware results: received={}, persisted={}, readOnly={}",
                    response.getReceivedCount(), response.getPersistedCount(), response.getReadOnlyCount());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid middleware result payload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(MiddlewareReceiveResultResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("Unexpected error while processing middleware results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MiddlewareReceiveResultResponse.error("Internal error processing result payload"));
        }
    }
}
