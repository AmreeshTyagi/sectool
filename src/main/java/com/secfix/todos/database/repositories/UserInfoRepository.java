package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserInfoRepository extends JpaRepository<UserInfo, Integer> {
    Optional<UserInfo> findByEmailAndTenantId(String email, UUID tenantId);
    List<UserInfo> findByTenantId(UUID tenantId);
}
