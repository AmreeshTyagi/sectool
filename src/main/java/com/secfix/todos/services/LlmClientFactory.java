package com.secfix.todos.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmClientFactory {

    @Bean
    public LlmClient llmClient(
            @Value("${sectool.llm.provider}") String provider,
            @Value("${sectool.llm.base-url}") String baseUrl,
            @Value("${sectool.llm.api-key:}") String apiKey,
            @Value("${sectool.llm.model}") String model) {
        return switch (provider.toLowerCase()) {
            case "anthropic" -> new AnthropicLlmClient(baseUrl, apiKey, model);
            case "openai" -> new OpenAiLlmClient(baseUrl, apiKey, model);
            default -> new OllamaLlmClient(baseUrl, model);
        };
    }
}
