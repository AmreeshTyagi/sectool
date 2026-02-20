package com.secfix.todos.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingsClientFactory {

    @Bean
    public EmbeddingsClient embeddingsClient(
            @Value("${sectool.embeddings.provider}") String provider,
            @Value("${sectool.embeddings.base-url}") String baseUrl,
            @Value("${sectool.embeddings.api-key:}") String apiKey,
            @Value("${sectool.embeddings.model}") String model,
            @Value("${sectool.embeddings.dimensions}") int dimensions) {
        return switch (provider.toLowerCase()) {
            case "anthropic", "voyage", "openai" -> new OpenAiEmbeddingsClient(baseUrl, apiKey, model, dimensions);
            default -> new OllamaEmbeddingsClient(baseUrl, model, dimensions);
        };
    }
}
