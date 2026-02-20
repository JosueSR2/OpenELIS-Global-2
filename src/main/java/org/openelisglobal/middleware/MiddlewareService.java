package org.openelisglobal.middleware;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class MiddlewareService {

   private static final String TEST_ENDPOINT = "test";
    private static final String SEND_COMMAND_ENDPOINT = "send-command";
    private static final String GET_RESULTS_ENDPOINT = "get-results";

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final URI middlewareBaseUri;
    private final String apiKey;

    public MiddlewareService(
            @Value("${org.openelisglobal.middleware.base-url:https://localhost:5284/api/analyzer/}") String baseUrl,
            @Value("${org.openelisglobal.middleware.allow-http:false}") boolean allowHttp,
            @Value("${org.openelisglobal.middleware.api-key:}") String apiKey,
            @Value("${org.openelisglobal.middleware.timeout-seconds:30}") long timeoutSeconds,
            ObjectMapper objectMapper) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
        this.objectMapper = objectMapper;
        this.middlewareBaseUri = validateBaseUrl(baseUrl, allowHttp);
        this.apiKey = apiKey;
    }

    public String testConnection() throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(TEST_ENDPOINT).GET().build();
        return execute(request);
    }

public String sendCommand(String command) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(SEND_COMMAND_ENDPOINT)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(command))

.build();
        return execute(request);
    }

    public List<String> getResults() throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(GET_RESULTS_ENDPOINT).GET().build();
        String responseBody = execute(request);
        return objectMapper.readValue(responseBody, new TypeReference<List<String>>() {
        });
    }

    private HttpRequest.Builder requestBuilder(String endpoint) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(middlewareBaseUri.resolve(endpoint))
                .timeout(Duration.ofSeconds(30));

        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("X-API-Key", apiKey);
        }

        return builder;
    }

    private String execute(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Middleware call failed with status " + response.statusCode());
        }
        return response.body();
    }
private URI validateBaseUrl(String baseUrl, boolean allowHttp) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid middleware URL: " + baseUrl, e);
        }
if (!allowHttp && "http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "HTTP middleware URLs are disabled. Use HTTPS or set org.openelisglobal.middleware.allow-http=true for development.");
        }

        return uri;
    }
}
