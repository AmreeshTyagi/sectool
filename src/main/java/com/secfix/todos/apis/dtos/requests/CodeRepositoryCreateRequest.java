package com.secfix.todos.apis.dtos.requests;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class CodeRepositoryCreateRequest {
    @NotNull(message = "Name can't be null")
    @NotEmpty(message = "Name can't be empty")
    private String name;

    @NotNull(message = "Owner can't be null")
    @NotEmpty(message = "Owner can't be empty")
    private String owner;

    @Override
    public String toString() {
        return String.format("Name: %s", this.getName());
    }
}
