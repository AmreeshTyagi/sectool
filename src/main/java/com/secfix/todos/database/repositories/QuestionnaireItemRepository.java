package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.QuestionnaireItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionnaireItemRepository extends JpaRepository<QuestionnaireItem, UUID> {

    List<QuestionnaireItem> findByTenantIdAndQuestionnaireIdOrderByItemIndex(UUID tenantId, UUID questionnaireId);
}
