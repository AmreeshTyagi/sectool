package com.secfix.todos.database.models;

import com.secfix.todos.apis.dtos.requests.CodeRepositoryCreateRequest;
import com.secfix.todos.enums.CodeRepositoryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Date;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "code_repository")
@Data
public class CodeRepository {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    /**
     * Example: Equals to Github repository name
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Example: Equals to Github repository owner login
     */
    @Column(name = "owner", nullable = false)
    private String owner;

     /**
     * Example: Equals to Github repository url
     */
    private String url;

    /**
     * Example: Equals to Github repository stars
     */
    private Integer stars;

   /**
     * Example: Equals to Github repository open issues count
     */
    private Integer openIssuesCount;

    /**
     * Example: Equals to Github repository license key
     */
    private String license;

    /**
     * Example: Equals to Github repository created at
     */
    private Date createdAt;

    /**
     * Example: Equals to Github repository updated at
     */
    private Date updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, name = "status", columnDefinition = "VARCHAR(32) DEFAULT 'ACTIVE'")
    private CodeRepositoryStatus status;

    public CodeRepository() {
    }

    public CodeRepository(CodeRepositoryCreateRequest request) {
        this.setName(request.getName());
        this.setOwner(request.getOwner());
        this.setStatus(CodeRepositoryStatus.ACTIVE);
    }
}
