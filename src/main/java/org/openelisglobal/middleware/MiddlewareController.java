package org.openelisglobal.middleware;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/middleware")
public class MiddlewareController {

    private final MiddlewareService service = new MiddlewareService();

    @GetMapping("/test")
    public ResponseEntity<String> test() throws Exception {
        return ResponseEntity.ok(service.testConnection());
    }

    @GetMapping("/results")
    public ResponseEntity<List<String>> getResults() throws Exception {
        return ResponseEntity.ok(Arrays.asList(service.getResults()));
    }

    @PostMapping("/receive-result")
    public ResponseEntity<String> receiveResult(@RequestBody String json) {

        System.out.println("Result received from middleware:");
        System.out.println(json);

        return ResponseEntity.ok("Received");
    }
}


