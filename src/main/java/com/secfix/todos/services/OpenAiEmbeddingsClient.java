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

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 20_000;

    @Override
    public List<float[]> embed(List<String> texts) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String body = objectMapper.writeValueAsString(Map.of("model", model, "input", texts));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/embeddings"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429) {
                    long backoff = INITIAL_BACKOFF_MS * (1L << attempt);
                    logger.warn("Embeddings API rate-limited (429), retrying in {}s (attempt {}/{})",
                            backoff / 1000, attempt + 1, MAX_RETRIES);
                    Thread.sleep(backoff);
                    continue;
                }

                if (response.statusCode() != 200) {
                    String snippet = response.body().length() > 300 ? response.body().substring(0, 300) : response.body();
                    logger.error("Embeddings API returned status {}: {}", response.statusCode(), snippet);
                    return zeroVectors(texts.size());
                }

                JsonNode json = objectMapper.readTree(response.body());
                JsonNode data = json.get("data");
                if (data == null || !data.isArray()) {
                    logger.error("Embeddings API response missing 'data' field. Response: {}",
                            response.body().length() > 300 ? response.body().substring(0, 300) : response.body());
                    return zeroVectors(texts.size());
                }

                List<float[]> results = new ArrayList<>();
                for (JsonNode item : data) {
                    JsonNode embeddingNode = item.get("embedding");
                    if (embeddingNode == null || !embeddingNode.isArray()) {
                        logger.warn("Embeddings API returned item without 'embedding' array");
                        results.add(new float[dims]);
                        continue;
                    }
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    results.add(embedding);
                }
                return results;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Embedding interrupted during backoff", e);
                return zeroVectors(texts.size());
            } catch (Exception e) {
                logger.error("Embeddings call failed", e);
                return zeroVectors(texts.size());
            }
        }
        logger.error("Embeddings API rate-limited after {} retries, returning zero vectors", MAX_RETRIES);
        return zeroVectors(texts.size());
    }

    private List<float[]> zeroVectors(int count) {
        List<float[]> fallback = new ArrayList<>();
        for (int i = 0; i < count; i++) fallback.add(new float[dims]);
        return fallback;
    }

    @Override
    public int dimensions() { return dims; }
}
