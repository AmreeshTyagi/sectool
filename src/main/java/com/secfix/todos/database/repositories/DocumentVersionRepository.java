package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByTenantIdAndDocumentId(UUID tenantId, UUID documentId);

    Optional<DocumentVersion> findByTenantIdAndId(UUID tenantId, UUID id);
}
