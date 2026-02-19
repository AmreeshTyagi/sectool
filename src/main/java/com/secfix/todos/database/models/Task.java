package com.secfix.todos.database.models;

import com.secfix.todos.apis.dtos.requests.TaskCreateRequest;
import com.secfix.todos.enums.TaskPriority;
import com.secfix.todos.enums.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;


@Entity
@Table(name = "task")
@Data
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, name = "priority", nullable = false)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, name = "status", nullable = false)
    private TaskStatus status;

    @ManyToOne
    @JoinColumn(name = "owner", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserInfo owner;

    @ManyToOne
    @JoinColumn(name = "code_repository", nullable = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CodeRepository codeRepository;

    public Task() {
    }

    public Task(TaskCreateRequest request, UserInfo owner, CodeRepository codeRepository) {
        this.setName(request.getName());
        this.setDescription(request.getDescription());
        this.setPriority(request.getPriority());
        this.setStatus(request.getStatus());
        this.setOwner(owner);
        this.setCodeRepository(codeRepository);
    }
}
