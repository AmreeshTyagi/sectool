package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.Questionnaire;
import com.secfix.todos.enums.QuestionnaireStatus;
import com.secfix.todos.enums.QuestionnaireType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface QuestionnaireRepository extends JpaRepository<Questionnaire, UUID>, JpaSpecificationExecutor<Questionnaire> {

    List<Questionnaire> findByTenantId(UUID tenantId);

    List<Questionnaire> findByTenantIdAndStatus(UUID tenantId, QuestionnaireStatus status);

    List<Questionnaire> findByTenantIdAndType(UUID tenantId, QuestionnaireType type);

    List<Questionnaire> findByTenantIdAndOwnerUserId(UUID tenantId, Integer ownerUserId);
}
