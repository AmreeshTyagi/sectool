package com.secfix.todos.database.models;

import com.secfix.todos.enums.QuestionnaireItemState;
import com.secfix.todos.enums.ResponseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "questionnaire_item")
@Data
public class QuestionnaireItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "questionnaire_id", nullable = false)
    private UUID questionnaireId;

    @Column(name = "item_index")
    private Integer itemIndex;

    @Column(name = "question_text", columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type")
    private ResponseType responseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state")
    private QuestionnaireItemState currentState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_location", columnDefinition = "jsonb")
    private String sourceLocation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
