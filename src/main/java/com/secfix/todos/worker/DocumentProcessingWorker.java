package com.secfix.todos.worker;

import com.secfix.todos.database.models.*;
import com.secfix.todos.database.repositories.*;
import com.secfix.todos.enums.*;
import com.secfix.todos.services.*;
import com.secfix.todos.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DocumentProcessingWorker {
    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingWorker.class);

    private final DocumentProcessingJobRepository jobRepo;
    private final DocumentVersionRepository versionRepo;
    private final DocumentArtifactRepository artifactRepo;
    private final DocumentRepository documentRepo;
    private final KbChunkRepository chunkRepo;
    private final KbEmbeddingRepository embeddingRepo;
    private final StorageService storageService;
    private final KreuzbergClient kreuzbergClient;
    private final ChunkingService chunkingService;
    private final EmbeddingsClient embeddingsClient;
    private final QuestionnaireExtractionService questionnaireExtractionService;
    private final SpreadsheetParsingService spreadsheetParsingService;
    private final String workerId;

    @Value("${sectool.worker.max-attempts}")
    private int maxAttempts;

    public DocumentProcessingWorker(
            DocumentProcessingJobRepository jobRepo,
            DocumentVersionRepository versionRepo,
            DocumentArtifactRepository artifactRepo,
            DocumentRepository documentRepo,
            KbChunkRepository chunkRepo,
            KbEmbeddingRepository embeddingRepo,
            StorageService storageService,
            KreuzbergClient kreuzbergClient,
            ChunkingService chunkingService,
            EmbeddingsClient embeddingsClient,
            QuestionnaireExtractionService questionnaireExtractionService,
            SpreadsheetParsingService spreadsheetParsingService) {
        this.jobRepo = jobRepo;
        this.versionRepo = versionRepo;
        this.artifactRepo = artifactRepo;
        this.documentRepo = documentRepo;
        this.chunkRepo = chunkRepo;
        this.embeddingRepo = embeddingRepo;
        this.storageService = storageService;
        this.kreuzbergClient = kreuzbergClient;
        this.chunkingService = chunkingService;
        this.embeddingsClient = embeddingsClient;
        this.questionnaireExtractionService = questionnaireExtractionService;
        this.spreadsheetParsingService = spreadsheetParsingService;
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(fixedDelayString = "${sectool.worker.poll-interval-ms:5000}")
    public void pollAndProcess() {
        for (ProcessingJobStage stage : ProcessingJobStage.values()) {
            try {
                processStage(stage);
            } catch (Exception e) {
                logger.error("Error processing stage {}", stage, e);
            }
        }
    }

    @Transactional
    public void processStage(ProcessingJobStage stage) {
        DocumentProcessingJob job = jobRepo.findNextPendingJob(stage).orElse(null);
        if (job == null) return;

        job.setStatus(ProcessingJobStatus.RUNNING);
        job.setLockedAt(Instant.now());
        job.setLockedBy(workerId);
        job.setAttempt(job.getAttempt() + 1);
        job.setUpdatedAt(Instant.now());
        jobRepo.save(job);

        logger.info("Processing job {} stage {} attempt {}", job.getId(), stage, job.getAttempt());

        try {
            DocumentVersion version = versionRepo.findById(job.getDocumentVersionId()).orElseThrow();

            switch (stage) {
                case PARSE -> handleParse(job, version);
                case EXTRACT_QUESTIONS -> handleExtractQuestions(job, version);
                case CHUNK -> handleChunk(job, version);
                case EMBED -> handleEmbed(job, version);
                case FINALIZE -> handleFinalize(job, version);
            }

            job.setStatus(ProcessingJobStatus.DONE);
            job.setUpdatedAt(Instant.now());
            jobRepo.save(job);

            ProcessingJobStage nextStage = getNextStage(stage, version);
            if (nextStage != null) {
                DocumentProcessingJob nextJob = new DocumentProcessingJob();
                nextJob.setTenantId(job.getTenantId());
                nextJob.setDocumentVersionId(job.getDocumentVersionId());
                nextJob.setStage(nextStage);
                nextJob.setStatus(ProcessingJobStatus.PENDING);
                nextJob.setAttempt(0);
                jobRepo.save(nextJob);
            }
        } catch (Exception e) {
            logger.error("Job {} failed", job.getId(), e);
            if (job.getAttempt() >= maxAttempts) {
                job.setStatus(ProcessingJobStatus.FAILED);
                job.setErrorMessage(e.getMessage());
                DocumentVersion version = versionRepo.findById(job.getDocumentVersionId()).orElse(null);
                if (version != null) {
                    version.setStatus(DocumentVersionStatus.FAILED);
                    versionRepo.save(version);
                }
            } else {
                job.setStatus(ProcessingJobStatus.PENDING);
                job.setLockedAt(null);
                job.setLockedBy(null);
            }
            job.setUpdatedAt(Instant.now());
            jobRepo.save(job);
        }
    }

    private static final java.util.Set<String> SPREADSHEET_MIMES = java.util.Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/x-excel"
    );

    private void handleParse(DocumentProcessingJob job, DocumentVersion version) {
        byte[] original = storageService.getObject(version.getObjectKeyOriginal());
        KreuzbergClient.KreuzbergResult kreuzbergResult = kreuzbergClient.parse(
                original, version.getOriginalFilename(), version.getMimeType());

        UUID tenantId = job.getTenantId();
        UUID docId = version.getDocumentId();
        UUID verId = version.getId();

        storeArtifact(tenantId, docId, verId, DocumentArtifactKind.EXTRACTED_TEXT,
                kreuzbergResult.extractedText().getBytes(), "text/plain");

        boolean isSpreadsheet = SPREADSHEET_MIMES.contains(version.getMimeType()) ||
                (version.getOriginalFilename() != null && version.getOriginalFilename().endsWith(".xlsx"));

        if (isSpreadsheet) {
            List<SpreadsheetParsingService.SheetData> sheets = spreadsheetParsingService.parseXlsx(original);
            if (!sheets.isEmpty()) {
                String structuredJson = spreadsheetParsingService.buildStructuredJson(
                        kreuzbergResult.extractedText(), null, sheets);
                storeArtifact(tenantId, docId, verId, DocumentArtifactKind.PARSED_JSON,
                        structuredJson.getBytes(), "application/json");

                String richHtml = spreadsheetParsingService.buildRichHtml(sheets, version.getOriginalFilename());
                storeArtifact(tenantId, docId, verId, DocumentArtifactKind.RENDERED_HTML,
                        richHtml.getBytes(), "text/html");
                logger.info("Parsed spreadsheet with {} sheets using POI for version {}", sheets.size(), verId);
                return;
            }
        }

        storeArtifact(tenantId, docId, verId, DocumentArtifactKind.PARSED_JSON,
                kreuzbergResult.fullJson().getBytes(), "application/json");
        storeArtifact(tenantId, docId, verId, DocumentArtifactKind.RENDERED_HTML,
                kreuzbergResult.renderedHtml().getBytes(), "text/html");
    }

    private void handleExtractQuestions(DocumentProcessingJob job, DocumentVersion version) {
        Document document = documentRepo.findById(version.getDocumentId()).orElse(null);
        if (document == null || document.getType() != DocumentType.QUESTIONNAIRE) {
            logger.info("Skipping EXTRACT_QUESTIONS for non-questionnaire document {}", version.getDocumentId());
            return;
        }

        String parsedJson = loadArtifactText(job.getTenantId(), version.getId(), DocumentArtifactKind.PARSED_JSON);
        if (parsedJson == null) {
            logger.warn("No PARSED_JSON artifact found for version {}", version.getId());
            return;
        }

        QuestionnaireExtractionService.ExtractionResult result =
                questionnaireExtractionService.extract(
                        job.getTenantId(), version.getId(),
                        document.getTitle(), parsedJson);

        if (result != null) {
            logger.info("Extracted questionnaire {} with {} items from sheet '{}'",
                    result.questionnaireId(), result.itemsCreated(), result.sheetName());
        }
    }

    private void handleChunk(DocumentProcessingJob job, DocumentVersion version) {
        String extractedText = loadArtifactText(job.getTenantId(), version.getId(), DocumentArtifactKind.EXTRACTED_TEXT);
        String parsedJson = loadArtifactText(job.getTenantId(), version.getId(), DocumentArtifactKind.PARSED_JSON);

        List<ChunkingService.ChunkResult> chunks;
        if (parsedJson != null) {
            chunks = chunkingService.chunk(extractedText, version.getMimeType(), parsedJson);
        } else if (extractedText != null) {
            chunks = chunkingService.chunk(extractedText, version.getMimeType());
        } else {
            throw new RuntimeException("No text or parsed JSON artifact found for version " + version.getId());
        }

        for (ChunkingService.ChunkResult cr : chunks) {
            KbChunk chunk = new KbChunk();
            chunk.setTenantId(job.getTenantId());
            chunk.setDocumentVersionId(version.getId());
            chunk.setChunkIndex(cr.index());
            chunk.setText(cr.text());
            chunk.setMetadata(cr.metadata());
            chunkRepo.save(chunk);
        }
    }

    private void handleEmbed(DocumentProcessingJob job, DocumentVersion version) {
        List<KbChunk> chunks = chunkRepo.findByTenantIdAndDocumentVersionId(
                job.getTenantId(), version.getId());
        if (chunks.isEmpty()) return;

        List<String> texts = chunks.stream().map(KbChunk::getText).collect(Collectors.toList());
        List<float[]> embeddings = embeddingsClient.embed(texts);

        for (int i = 0; i < chunks.size(); i++) {
            KbEmbedding emb = new KbEmbedding();
            emb.setTenantId(job.getTenantId());
            emb.setChunkId(chunks.get(i).getId());
            emb.setEmbeddingModel("default");
            emb.setEmbedding(serializeEmbedding(embeddings.get(i)));
            embeddingRepo.save(emb);
        }
    }

    private void handleFinalize(DocumentProcessingJob job, DocumentVersion version) {
        version.setStatus(DocumentVersionStatus.READY);
        versionRepo.save(version);
        logger.info("Document version {} is READY", version.getId());
    }

    private String loadArtifactText(UUID tenantId, UUID versionId, DocumentArtifactKind kind) {
        List<DocumentArtifact> artifacts = artifactRepo.findByTenantIdAndDocumentVersionId(tenantId, versionId);
        return artifacts.stream()
                .filter(a -> a.getKind() == kind)
                .findFirst()
                .map(a -> new String(storageService.getObject(a.getObjectKey())))
                .orElse(null);
    }

    private void storeArtifact(UUID tenantId, UUID docId, UUID verId,
                                DocumentArtifactKind kind, byte[] data, String contentType) {
        String objectKey = storageService.buildObjectKey(tenantId, docId, verId, "artifacts/" + kind.name().toLowerCase());
        storageService.putObject(objectKey, data, contentType);

        DocumentArtifact artifact = new DocumentArtifact();
        artifact.setTenantId(tenantId);
        artifact.setDocumentVersionId(verId);
        artifact.setKind(kind);
        artifact.setObjectKey(objectKey);
        artifact.setContentType(contentType);
        artifactRepo.save(artifact);
    }

    private String serializeEmbedding(float[] embedding) {
        return Arrays.toString(embedding);
    }

    private ProcessingJobStage getNextStage(ProcessingJobStage current, DocumentVersion version) {
        Document document = documentRepo.findById(version.getDocumentId()).orElse(null);
        boolean isQuestionnaire = document != null && document.getType() == DocumentType.QUESTIONNAIRE;

        return switch (current) {
            case PARSE -> isQuestionnaire ? ProcessingJobStage.EXTRACT_QUESTIONS : ProcessingJobStage.CHUNK;
            case EXTRACT_QUESTIONS -> ProcessingJobStage.CHUNK;
            case CHUNK -> ProcessingJobStage.EMBED;
            case EMBED -> ProcessingJobStage.FINALIZE;
            case FINALIZE -> null;
        };
    }
}
