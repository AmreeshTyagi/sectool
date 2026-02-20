package com.secfix.todos.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class AnthropicLlmClient implements LlmClient {
    private static final Logger logger = LoggerFactory.getLogger(AnthropicLlmClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnthropicLlmClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        try {
            var messages = List.of(
                Map.of("role", "user", "content", userPrompt)
            );
            var payload = Map.of(
                "model", model,
                "max_tokens", DEFAULT_MAX_TOKENS,
                "system", systemPrompt,
                "messages", messages
            );
            String body = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Anthropic API returned status {}: {}", response.statusCode(),
                        response.body().length() > 500 ? response.body().substring(0, 500) : response.body());
                return "Error: Anthropic API returned status " + response.statusCode();
            }

            JsonNode json = objectMapper.readTree(response.body());
            JsonNode content = json.get("content");
            if (content != null && content.isArray() && !content.isEmpty()) {
                return content.get(0).path("text").asText("");
            }

            logger.warn("Anthropic response had no content: {}", response.body());
            return "";
        } catch (Exception e) {
            logger.error("Anthropic LLM call failed", e);
            return "Error generating response: " + e.getMessage();
        }
    }
}
