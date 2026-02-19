package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.AnswerLibraryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnswerLibraryEntryRepository extends JpaRepository<AnswerLibraryEntry, UUID> {

    List<AnswerLibraryEntry> findByTenantId(UUID tenantId);
}
