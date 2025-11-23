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
import java.util.Map;
import java.util.Objects;

/**
 * Ollama-compatible client hitting the /api/generate endpoint.
 */
public class OllamaLlmClient implements RagLlmClient {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public OllamaLlmClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl is required");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = Objects.requireNonNull(model, "model is required");
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) throws Exception {
        String prompt = systemPrompt + "\n\n" + userPrompt;

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("prompt", prompt);
        payload.put("stream", false);

        String body = mapper.writeValueAsString(payload);

        String target = buildUrl();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(target))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (!apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("LLM API error: HTTP " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        if (root == null) throw new IOException("Empty response from Ollama");

        // Ollama /api/generate typically returns {"response":"..."} when stream=false
        if (root.has("response")) {
            return root.get("response").asText();
        }
        // Fallback to OpenAI-like shape if proxied
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode content = choices.get(0).path("message").path("content");
            if (content != null && !content.isMissingNode()) return content.asText();
        }
        throw new IOException("LLM API did not return content");
    }

    private String buildUrl() {
        if (baseUrl.endsWith("/api/generate")) return baseUrl;
        if (baseUrl.endsWith("/")) return baseUrl + "api/generate";
        return baseUrl + "/api/generate";
    }
}
