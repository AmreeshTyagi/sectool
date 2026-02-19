package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.AnswerSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnswerSuggestionRepository extends JpaRepository<AnswerSuggestion, UUID> {

    List<AnswerSuggestion> findByTenantIdAndQuestionnaireItemId(UUID tenantId, UUID questionnaireItemId);
}
