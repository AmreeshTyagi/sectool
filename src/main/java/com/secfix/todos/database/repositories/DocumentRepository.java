package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByTenantId(UUID tenantId);
}
