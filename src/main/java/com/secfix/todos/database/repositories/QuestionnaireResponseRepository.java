package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.QuestionnaireResponse;
import com.secfix.todos.enums.ResponseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionnaireResponseRepository extends JpaRepository<QuestionnaireResponse, UUID> {

    List<QuestionnaireResponse> findByTenantIdAndQuestionnaireItemId(UUID tenantId, UUID questionnaireItemId);

    List<QuestionnaireResponse> findByTenantIdAndStatus(UUID tenantId, ResponseStatus status);
}
