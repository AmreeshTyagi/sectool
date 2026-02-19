package com.secfix.todos.apis.dtos.requests;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String name;
    private String email;
    private Boolean isActive;

    @Override
    public String toString() {
        return String.format("Name: %s, Email: %s", this.getName() != null? this.getName(): "", this.getEmail() != null? this.getEmail(): "");
    }
}
