package com.secfix.todos.services;

public interface LlmClient {
    String complete(String systemPrompt, String userPrompt);
}
