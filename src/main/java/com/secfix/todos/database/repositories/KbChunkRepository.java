package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.KbChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KbChunkRepository extends JpaRepository<KbChunk, UUID> {

    List<KbChunk> findByTenantIdAndDocumentVersionId(UUID tenantId, UUID documentVersionId);
}
