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

public class OllamaEmbeddingsClient implements EmbeddingsClient {
    private static final Logger logger = LoggerFactory.getLogger(OllamaEmbeddingsClient.class);
    private final String baseUrl;
    private final String model;
    private final int dims;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaEmbeddingsClient(String baseUrl, String model, int dimensions) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.dims = dimensions;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            try {
                String body = objectMapper.writeValueAsString(Map.of("model", model, "prompt", text));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/embeddings"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode json = objectMapper.readTree(response.body());
                JsonNode embeddingNode = json.get("embedding");
                if (embeddingNode == null || !embeddingNode.isArray()) {
                    logger.warn("Ollama returned no embedding (service may not be running). Response: {}",
                            response.body().length() > 200 ? response.body().substring(0, 200) : response.body());
                    results.add(new float[dims]);
                } else {
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    results.add(embedding);
                }
            } catch (Exception e) {
                logger.error("Ollama embedding failed for text chunk", e);
                results.add(new float[dims]);
            }
        }
        return results;
    }

    @Override
    public int dimensions() { return dims; }
}
