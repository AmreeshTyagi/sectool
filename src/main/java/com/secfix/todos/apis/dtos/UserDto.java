package com.secfix.todos.apis.dtos;

import com.secfix.todos.database.models.UserInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Data
@NoArgsConstructor
public class UserDto {
  
    private Integer id;
    private UUID tenantId;
    private String name;
    private String email;
    private String role;
    private Boolean isActive;

    public UserDto(UserInfo user) {
        this.setId(user.getId());
        this.setTenantId(user.getTenantId());
        this.setName(user.getName());
        this.setEmail(user.getEmail());
        this.setRole(user.getRole());
        this.setIsActive(user.getIsActive());
    }
}
