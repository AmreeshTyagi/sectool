package com.secfix.todos.apis.controllers;

import com.secfix.todos.database.models.*;
import com.secfix.todos.database.repositories.*;
import com.secfix.todos.enums.FeedbackThumb;
import com.secfix.todos.services.QuestionnairesService;
import com.secfix.todos.services.RagService;
import com.secfix.todos.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin
@RestController
public class RagController {

    private final RagService ragService;
    private final QuestionnairesService questionnairesService;
    private final KbChunkRepository chunkRepo;
    private final DocumentVersionRepository documentVersionRepo;
    private final DocumentRepository documentRepo;

    public RagController(RagService ragService, QuestionnairesService questionnairesService,
                         KbChunkRepository chunkRepo, DocumentVersionRepository documentVersionRepo,
                         DocumentRepository documentRepo) {
        this.ragService = ragService;
        this.questionnairesService = questionnairesService;
        this.chunkRepo = chunkRepo;
        this.documentVersionRepo = documentVersionRepo;
        this.documentRepo = documentRepo;
    }

    @Operation(summary = "Generate suggestion")
    @PostMapping("/rag/suggest")
    public ResponseEntity<?> suggest(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        UUID tenantId = TenantContext.getTenantId();
        RagService.SuggestionResult result = ragService.suggest(question, tenantId);
        return ResponseEntity.ok(Map.of(
                "answerText", result.answerText(),
                "citations", result.citations(),
                "confidence", result.confidence(),
                "coverageStatus", result.coverageStatus().name()
        ));
    }

    @Operation(summary = "Submit feedback")
    @PostMapping("/rag/feedback")
    public ResponseEntity<?> feedback(@RequestBody Map<String, String> body) {
        UUID suggestionId = UUID.fromString(body.get("suggestionId"));
        FeedbackThumb thumb = FeedbackThumb.valueOf(body.get("thumb"));
        String comment = body.get("comment");
        return ResponseEntity.ok(questionnairesService.saveFeedback(suggestionId, thumb, comment));
    }

    @Operation(summary = "Get pending import answers count")
    @GetMapping("/imports/pending-answers")
    public ResponseEntity<?> pendingAnswers() {
        long count = questionnairesService.countPendingImportAnswers();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(summary = "List pending import answers with details")
    @GetMapping("/imports/pending-answers/list")
    public ResponseEntity<?> listPendingAnswers() {
        return ResponseEntity.ok(questionnairesService.listPendingImportAnswers());
    }

    @Operation(summary = "Import selected answers to answer library")
    @PostMapping("/imports/pending-answers/import")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> importAnswers(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("responseIds");
        List<UUID> responseIds = ids.stream().map(UUID::fromString).toList();
        int imported = questionnairesService.importAnswersToLibrary(responseIds);
        return ResponseEntity.ok(Map.of("imported", imported));
    }

    @Operation(summary = "Get knowledge base chunk by ID")
    @GetMapping("/kb/chunks/{chunkId}")
    public ResponseEntity<?> getChunk(@PathVariable UUID chunkId) {
        UUID tenantId = TenantContext.getTenantId();
        KbChunk chunk = chunkRepo.findById(chunkId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElse(null);
        if (chunk == null) {
            return ResponseEntity.notFound().build();
        }

        String documentTitle = null;
        String documentType = null;
        DocumentVersion version = documentVersionRepo.findById(chunk.getDocumentVersionId()).orElse(null);
        if (version != null) {
            Document doc = documentRepo.findById(version.getDocumentId()).orElse(null);
            if (doc != null) {
                documentTitle = doc.getTitle();
                documentType = doc.getType() != null ? doc.getType().name() : null;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", chunk.getId());
        result.put("text", chunk.getText());
        result.put("metadata", chunk.getMetadata());
        result.put("chunkIndex", chunk.getChunkIndex());
        result.put("documentTitle", documentTitle);
        result.put("documentType", documentType);
        result.put("versionNum", version != null ? version.getVersionNum() : null);
        return ResponseEntity.ok(result);
    }
}
