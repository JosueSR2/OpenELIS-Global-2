package org.openelis.middleware;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MiddlewareService {

    private final HttpClient client = HttpClient.newHttpClient();
    private final String baseUrl = "http://localhost:5284/api/analyzer/";

    public String testConnection() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "test"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String sendCommand(String command) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "send-command"))
                .POST(HttpRequest.BodyPublishers.ofString(command))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String[] getResults() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "get-results"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // Suponiendo que la API retorna un array JSON
        return response.body().replace("[","").replace("]","").split(",");
    }
}
