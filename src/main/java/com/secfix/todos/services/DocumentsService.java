package com.secfix.todos.services;

import com.secfix.todos.database.models.Document;
import com.secfix.todos.database.models.DocumentArtifact;
import com.secfix.todos.database.models.DocumentProcessingJob;
import com.secfix.todos.database.models.DocumentVersion;
import com.secfix.todos.database.repositories.DocumentArtifactRepository;
import com.secfix.todos.database.repositories.DocumentProcessingJobRepository;
import com.secfix.todos.database.repositories.DocumentRepository;
import com.secfix.todos.database.repositories.DocumentVersionRepository;
import com.secfix.todos.enums.DocumentArtifactKind;
import com.secfix.todos.enums.DocumentType;
import com.secfix.todos.enums.DocumentVersionStatus;
import com.secfix.todos.enums.ProcessingJobStage;
import com.secfix.todos.enums.ProcessingJobStatus;
import com.secfix.todos.exceptions.ApiServiceCallException;
import com.secfix.todos.storage.StorageService;
import com.secfix.todos.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentsService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentsService.class);
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(15);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentArtifactRepository documentArtifactRepository;
    private final DocumentProcessingJobRepository documentProcessingJobRepository;
    private final StorageService storageService;

    public DocumentsService(DocumentRepository documentRepository,
                            DocumentVersionRepository documentVersionRepository,
                            DocumentArtifactRepository documentArtifactRepository,
                            DocumentProcessingJobRepository documentProcessingJobRepository,
                            StorageService storageService) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentArtifactRepository = documentArtifactRepository;
        this.documentProcessingJobRepository = documentProcessingJobRepository;
        this.storageService = storageService;
    }

    public Document createDocument(String title, DocumentType type, String source) {
        UUID tenantId = TenantContext.getTenantId();
        Integer userId = TenantContext.getUserId();

        Document document = new Document();
        document.setTenantId(tenantId);
        document.setType(type);
        document.setTitle(title);
        document.setSource(source);
        document.setCreatedBy(userId);

        document = documentRepository.save(document);
        logger.info("Created document <{}> for tenant <{}>", document.getId(), tenantId);
        return document;
    }

    public Map<String, Object> createVersion(UUID documentId, String originalFilename,
                                              String mimeType, Long sizeBytes, String sha256) {
        UUID tenantId = TenantContext.getTenantId();

        Document document = documentRepository.findById(documentId)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ApiServiceCallException(
                        String.format("Document <%s> not found", documentId), HttpStatus.NOT_FOUND));

        int nextVersionNum = documentVersionRepository.findByTenantIdAndDocumentId(tenantId, document.getId()).size() + 1;

        DocumentVersion version = new DocumentVersion();
        version.setTenantId(tenantId);
        version.setDocumentId(document.getId());
        version.setVersionNum(nextVersionNum);
        version.setOriginalFilename(originalFilename);
        version.setMimeType(mimeType);
        version.setSizeBytes(sizeBytes);
        version.setSha256(sha256);
        version.setStatus(DocumentVersionStatus.UPLOADED);

        version = documentVersionRepository.save(version);

        String objectKey = storageService.buildObjectKey(tenantId, documentId, version.getId(), "original");
        version.setObjectKeyOriginal(objectKey);
        version = documentVersionRepository.save(version);

        String uploadUrl = storageService.generatePresignedPutUrl(objectKey, mimeType, PRESIGNED_URL_EXPIRATION);

        logger.info("Created version <{}> for document <{}>, tenant <{}>",
                version.getId(), documentId, tenantId);

        Map<String, Object> result = new HashMap<>();
        result.put("documentVersionId", version.getId());
        result.put("uploadUrl", uploadUrl);
        result.put("objectKey", objectKey);
        return result;
    }

    @Transactional
    public DocumentVersion completeUpload(UUID versionId) {
        UUID tenantId = TenantContext.getTenantId();

        DocumentVersion version = documentVersionRepository.findByTenantIdAndId(tenantId, versionId)
                .orElseThrow(() -> new ApiServiceCallException(
                        String.format("Document version <%s> not found", versionId), HttpStatus.NOT_FOUND));

        version.setStatus(DocumentVersionStatus.PROCESSING);
        version = documentVersionRepository.save(version);

        DocumentProcessingJob job = new DocumentProcessingJob();
        job.setTenantId(tenantId);
        job.setDocumentVersionId(version.getId());
        job.setStage(ProcessingJobStage.PARSE);
        job.setStatus(ProcessingJobStatus.PENDING);
        documentProcessingJobRepository.save(job);

        logger.info("Upload completed for version <{}>, processing job created", versionId);
        return version;
    }

    public Map<String, Object> getVersion(UUID versionId) {
        UUID tenantId = TenantContext.getTenantId();

        DocumentVersion version = documentVersionRepository.findByTenantIdAndId(tenantId, versionId)
                .orElseThrow(() -> new ApiServiceCallException(
                        String.format("Document version <%s> not found", versionId), HttpStatus.NOT_FOUND));

        List<DocumentArtifact> artifacts = documentArtifactRepository
                .findByTenantIdAndDocumentVersionId(tenantId, versionId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", version.getId());
        result.put("documentId", version.getDocumentId());
        result.put("versionNum", version.getVersionNum());
        result.put("originalFilename", version.getOriginalFilename());
        result.put("mimeType", version.getMimeType());
        result.put("sizeBytes", version.getSizeBytes());
        result.put("sha256", version.getSha256());
        result.put("status", version.getStatus());
        result.put("createdAt", version.getCreatedAt());
        result.put("artifacts", artifacts);
        return result;
    }

    public String getPreviewUrl(UUID versionId) {
        UUID tenantId = TenantContext.getTenantId();

        DocumentVersion version = documentVersionRepository.findByTenantIdAndId(tenantId, versionId)
                .orElseThrow(() -> new ApiServiceCallException(
                        String.format("Document version <%s> not found", versionId), HttpStatus.NOT_FOUND));

        return storageService.generatePresignedGetUrl(version.getObjectKeyOriginal(), PRESIGNED_URL_EXPIRATION);
    }

    public String getArtifactUrl(UUID versionId, DocumentArtifactKind kind) {
        UUID tenantId = TenantContext.getTenantId();

        documentVersionRepository.findByTenantIdAndId(tenantId, versionId)
                .orElseThrow(() -> new ApiServiceCallException(
                        String.format("Document version <%s> not found", versionId), HttpStatus.NOT_FOUND));

        List<DocumentArtifact> artifacts = documentArtifactRepository
                .findByTenantIdAndDocumentVersionId(tenantId, versionId);

        DocumentArtifact artifact = artifacts.stream()
                .filter(a -> a.getKind() == kind)
                .findFirst()
                .orElseThrow(() -> new ApiServiceCallException(
                        String.format("Artifact <%s> not found for version <%s>", kind, versionId),
                        HttpStatus.NOT_FOUND));

        return storageService.generatePresignedGetUrl(artifact.getObjectKey(), PRESIGNED_URL_EXPIRATION);
    }

    public String getArtifactContent(UUID versionId, DocumentArtifactKind kind) {
        UUID tenantId = TenantContext.getTenantId();

        documentVersionRepository.findByTenantIdAndId(tenantId, versionId)
                .orElseThrow(() -> new ApiServiceCallException(
                        String.format("Document version <%s> not found", versionId), HttpStatus.NOT_FOUND));

        List<DocumentArtifact> artifacts = documentArtifactRepository
                .findByTenantIdAndDocumentVersionId(tenantId, versionId);

        DocumentArtifact artifact = artifacts.stream()
                .filter(a -> a.getKind() == kind)
                .findFirst()
                .orElseThrow(() -> new ApiServiceCallException(
                        String.format("Artifact <%s> not found for version <%s>", kind, versionId),
                        HttpStatus.NOT_FOUND));

        byte[] data = storageService.getObject(artifact.getObjectKey());
        return new String(data);
    }

    public List<Map<String, Object>> listDocuments() {
        UUID tenantId = TenantContext.getTenantId();
        List<Document> documents = documentRepository.findByTenantId(tenantId);
        return documents.stream().map(doc -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", doc.getId());
            m.put("tenantId", doc.getTenantId());
            m.put("type", doc.getType());
            m.put("title", doc.getTitle());
            m.put("source", doc.getSource());
            m.put("createdBy", doc.getCreatedBy());
            m.put("createdAt", doc.getCreatedAt());
            List<DocumentVersion> versions = documentVersionRepository
                    .findByTenantIdAndDocumentId(tenantId, doc.getId());
            if (!versions.isEmpty()) {
                DocumentVersion latest = versions.get(0);
                m.put("latestVersionId", latest.getId());
                m.put("latestVersionStatus", latest.getStatus());
                m.put("originalFilename", latest.getOriginalFilename());
            }
            return m;
        }).toList();
    }
}
