package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.DocumentProcessingJob;
import com.secfix.todos.enums.ProcessingJobStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentProcessingJobRepository extends JpaRepository<DocumentProcessingJob, UUID> {

    List<DocumentProcessingJob> findByTenantIdAndDocumentVersionId(UUID tenantId, UUID documentVersionId);

    @Query(value = "SELECT * FROM document_processing_job WHERE status = 'PENDING' AND stage = :#{#stage.name()} " +
            "ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<DocumentProcessingJob> findNextPendingJob(@Param("stage") ProcessingJobStage stage);
}
