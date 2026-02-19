package com.secfix.todos.database.models;

import com.secfix.todos.enums.QuestionnaireStatus;
import com.secfix.todos.enums.QuestionnaireType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "questionnaire")
@Data
public class Questionnaire {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private QuestionnaireType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private QuestionnaireStatus status;

    @Column(name = "progress_percent")
    private Integer progressPercent = 0;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "owner_user_id")
    private Integer ownerUserId;

    @Column(name = "approver_user_id")
    private Integer approverUserId;

    @Column(name = "source_document_version_id")
    private UUID sourceDocumentVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
