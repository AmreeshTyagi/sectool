package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.AnswerFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnswerFeedbackRepository extends JpaRepository<AnswerFeedback, UUID> {

    List<AnswerFeedback> findByTenantIdAndAnswerSuggestionId(UUID tenantId, UUID answerSuggestionId);
}
