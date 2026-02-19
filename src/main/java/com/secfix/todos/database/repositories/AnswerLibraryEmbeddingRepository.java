package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.AnswerLibraryEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnswerLibraryEmbeddingRepository extends JpaRepository<AnswerLibraryEmbedding, UUID> {

    List<AnswerLibraryEmbedding> findByTenantIdAndEntryId(UUID tenantId, UUID entryId);
}
