package com.secfix.todos.apis.controllers;

import com.secfix.todos.database.models.Document;
import com.secfix.todos.database.models.DocumentVersion;
import com.secfix.todos.enums.DocumentArtifactKind;
import com.secfix.todos.enums.DocumentType;
import com.secfix.todos.exceptions.ApiServiceCallException;
import com.secfix.todos.services.DocumentsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin
@RestController
public class DocumentsController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentsController.class);

    private final DocumentsService documentsService;

    public DocumentsController(DocumentsService documentsService) {
        this.documentsService = documentsService;
    }

    @Operation(summary = "Create a new document")
    @PostMapping("/documents")
    public ResponseEntity<?> createDocument(@RequestBody Map<String, Object> body) {
        try {
            String title = (String) body.get("title");
            DocumentType type = DocumentType.valueOf((String) body.get("type"));
            String source = (String) body.get("source");

            Document document = documentsService.createDocument(title, type, source);
            return ResponseEntity.ok(Map.of("documentId", document.getId()));
        } catch (ApiServiceCallException ex) {
            return new ResponseEntity<>(ex.getMessage(), ex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error creating document", ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Create a new document version and get presigned upload URL")
    @PostMapping("/documents/{documentId}/versions")
    public ResponseEntity<?> createVersion(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @RequestBody Map<String, Object> body) {
        try {
            String originalFilename = (String) body.get("originalFilename");
            String mimeType = (String) body.get("mimeType");
            Long sizeBytes = body.get("sizeBytes") instanceof Number n ? n.longValue() : null;
            String sha256 = (String) body.get("sha256");

            Map<String, Object> result = documentsService.createVersion(
                    documentId, originalFilename, mimeType, sizeBytes, sha256);
            return ResponseEntity.ok(result);
        } catch (ApiServiceCallException ex) {
            return new ResponseEntity<>(ex.getMessage(), ex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error creating version for document <{}>", documentId, ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Mark version upload as complete and start processing")
    @PostMapping("/document-versions/{versionId}/complete")
    public ResponseEntity<?> completeUpload(
            @Parameter(description = "Document version ID") @PathVariable UUID versionId) {
        try {
            DocumentVersion version = documentsService.completeUpload(versionId);
            return ResponseEntity.ok(Map.of("status", version.getStatus().name()));
        } catch (ApiServiceCallException ex) {
            return new ResponseEntity<>(ex.getMessage(), ex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error completing upload for version <{}>", versionId, ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "List all documents for the current tenant")
    @GetMapping("/documents")
    public ResponseEntity<?> listDocuments() {
        try {
            var documents = documentsService.listDocuments();
            return ResponseEntity.ok(documents);
        } catch (ApiServiceCallException ex) {
            return new ResponseEntity<>(ex.getMessage(), ex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error listing documents", ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Get document version details with artifacts")
    @GetMapping("/document-versions/{versionId}")
    public ResponseEntity<?> getVersion(
            @Parameter(description = "Document version ID") @PathVariable UUID versionId) {
        try {
            Map<String, Object> result = documentsService.getVersion(versionId);
            return ResponseEntity.ok(result);
        } catch (ApiServiceCallException ex) {
            return new ResponseEntity<>(ex.getMessage(), ex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error getting version <{}>", versionId, ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Get presigned download URL for the original document")
    @GetMapping("/document-versions/{versionId}/preview")
    public ResponseEntity<?> getPreviewUrl(
            @Parameter(description = "Document version ID") @PathVariable UUID versionId) {
        try {
            String downloadUrl = documentsService.getPreviewUrl(versionId);
            return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
        } catch (ApiServiceCallException ex) {
            return new ResponseEntity<>(ex.getMessage(), ex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error getting preview URL for version <{}>", versionId, ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Get presigned download URL for a specific artifact")
    @GetMapping("/document-versions/{versionId}/artifacts/{kind}")
    public ResponseEntity<?> getArtifactUrl(
            @Parameter(description = "Document version ID") @PathVariable UUID versionId,
            @Parameter(description = "Artifact kind") @PathVariable DocumentArtifactKind kind) {
        try {
            String downloadUrl = documentsService.getArtifactUrl(versionId, kind);
            return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
        } catch (ApiServiceCallException ex) {
            return new ResponseEntity<>(ex.getMessage(), ex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error getting artifact URL for version <{}> kind <{}>", versionId, kind, ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Get artifact content directly (for in-app rendering)")
    @GetMapping("/document-versions/{versionId}/artifacts/{kind}/content")
    public ResponseEntity<?> getArtifactContent(
            @Parameter(description = "Document version ID") @PathVariable UUID versionId,
            @Parameter(description = "Artifact kind") @PathVariable DocumentArtifactKind kind) {
        try {
            String content = documentsService.getArtifactContent(versionId, kind);
            String contentType = kind == DocumentArtifactKind.PARSED_JSON ? "application/json"
                    : kind == DocumentArtifactKind.RENDERED_HTML ? "text/html"
                    : "text/plain";
            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .body(content);
        } catch (ApiServiceCallException ex) {
            return new ResponseEntity<>(ex.getMessage(), ex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error getting artifact content for version <{}> kind <{}>", versionId, kind, ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
