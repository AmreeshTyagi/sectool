package com.secfix.todos.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenAiEmbeddingsClient implements EmbeddingsClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiEmbeddingsClient.class);
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int dims;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiEmbeddingsClient(String baseUrl, String apiKey, String model, int dimensions) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.dims = dimensions;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("model", model, "input", texts));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode data = json.get("data");
            List<float[]> results = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.get("embedding");
                float[] embedding = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }
                results.add(embedding);
            }
            return results;
        } catch (Exception e) {
            logger.error("OpenAI embedding failed", e);
            List<float[]> fallback = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) fallback.add(new float[dims]);
            return fallback;
        }
    }

    @Override
    public int dimensions() { return dims; }
}
