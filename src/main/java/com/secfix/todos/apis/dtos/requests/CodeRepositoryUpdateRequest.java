package com.secfix.todos.apis.dtos.requests;

import com.secfix.todos.enums.CodeRepositoryStatus;
import lombok.Data;


@Data
public class CodeRepositoryUpdateRequest {
    private String name;
    private String owner;
    private CodeRepositoryStatus status;

    @Override
    public String toString() {
        return String.format("Name: %s", this.getName());
    }
}
