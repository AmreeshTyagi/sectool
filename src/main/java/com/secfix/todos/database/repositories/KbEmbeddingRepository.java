package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.KbEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KbEmbeddingRepository extends JpaRepository<KbEmbedding, UUID> {

    List<KbEmbedding> findByTenantIdAndChunkId(UUID tenantId, UUID chunkId);

    List<KbEmbedding> findByTenantId(UUID tenantId);
}
