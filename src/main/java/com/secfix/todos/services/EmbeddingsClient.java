package com.secfix.todos.services;

import java.util.List;

public interface EmbeddingsClient {
    List<float[]> embed(List<String> texts);
    int dimensions();
}
