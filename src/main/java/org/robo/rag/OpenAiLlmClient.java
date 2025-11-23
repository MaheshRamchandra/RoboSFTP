package org.robo.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal OpenAI-compatible client (works with OpenAI or any API exposing the chat/completions route).
 */
public class OpenAiLlmClient implements RagLlmClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public OpenAiLlmClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl is required");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey is required");
        this.model = Objects.requireNonNull(model, "model is required");
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        String body = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("LLM API error: HTTP " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode content = choices.get(0).path("message").path("content");
            if (content != null && !content.isMissingNode()) {
                return content.asText();
            }
        }
        throw new IOException("LLM API did not return content");
    }
}
