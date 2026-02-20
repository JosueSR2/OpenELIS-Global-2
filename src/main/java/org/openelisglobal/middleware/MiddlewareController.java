package org.openelisglobal.middleware;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/middleware")
public class MiddlewareController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MiddlewareController.class);

    private final MiddlewareService service;

    public MiddlewareController(MiddlewareService service) {
        this.service = service;
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
    public ResponseEntity<String> receiveResult(@RequestBody String json) {

        LOGGER.info("Result received from middleware: {}", json);
        return ResponseEntity.ok("Received");
    }
}


