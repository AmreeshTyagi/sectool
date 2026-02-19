package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.DocumentArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentArtifactRepository extends JpaRepository<DocumentArtifact, UUID> {

    List<DocumentArtifact> findByTenantIdAndDocumentVersionId(UUID tenantId, UUID documentVersionId);
}
