package com.secfix.todos.apis.controllers;

import com.secfix.todos.database.models.*;
import com.secfix.todos.enums.*;
import com.secfix.todos.services.QuestionnairesService;
import com.secfix.todos.services.SpreadsheetImportService;
import com.secfix.todos.storage.StorageService;
import com.secfix.todos.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/questionnaires")
public class QuestionnairesController {

    private final QuestionnairesService questionnairesService;
    private final SpreadsheetImportService spreadsheetImportService;
    private final StorageService storageService;

    public QuestionnairesController(QuestionnairesService questionnairesService,
                                     SpreadsheetImportService spreadsheetImportService,
                                     StorageService storageService) {
        this.questionnairesService = questionnairesService;
        this.spreadsheetImportService = spreadsheetImportService;
        this.storageService = storageService;
    }

    @Operation(summary = "List questionnaires")
    @GetMapping("")
    public ResponseEntity<?> list(@RequestParam(required = false) String status) {
        QuestionnaireStatus qs = status != null ? QuestionnaireStatus.valueOf(status) : null;
        return ResponseEntity.ok(questionnairesService.list(qs));
    }

    @Operation(summary = "Create questionnaire")
    @PostMapping("")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        QuestionnaireType type = QuestionnaireType.valueOf((String) body.get("type"));
        LocalDate dueDate = body.containsKey("dueDate") ? LocalDate.parse((String) body.get("dueDate")) : null;
        Integer ownerUserId = body.containsKey("ownerUserId") ? (Integer) body.get("ownerUserId") : TenantContext.getUserId();
        Questionnaire q = questionnairesService.create(name, type, dueDate, ownerUserId);
        return ResponseEntity.ok(Map.of("questionnaireId", q.getId()));
    }

    @Operation(summary = "Get questionnaire detail")
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        Questionnaire q = questionnairesService.getById(id);
        List<QuestionnaireItem> items = questionnairesService.getItems(id);
        return ResponseEntity.ok(Map.of("questionnaire", q, "items", items));
    }

    @Operation(summary = "Import spreadsheet")
    @PostMapping("/{id}/import/spreadsheet")
    public ResponseEntity<?> importSpreadsheet(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String objectKey = body.get("objectKey");
        byte[] fileBytes = storageService.getObject(objectKey);
        SpreadsheetImportService.ImportPreview preview = spreadsheetImportService.preview(fileBytes);
        String sessionId = UUID.randomUUID().toString();
        return ResponseEntity.ok(Map.of("importSessionId", sessionId, "preview", preview, "objectKey", objectKey));
    }

    @Operation(summary = "Submit column mappings")
    @PostMapping("/{id}/import/spreadsheet/columns")
    public ResponseEntity<?> submitColumnMappings(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String objectKey = (String) body.get("objectKey");
        @SuppressWarnings("unchecked")
        Map<String, String> mappings = (Map<String, String>) body.get("mappings");
        byte[] fileBytes = storageService.getObject(objectKey);
        int created = spreadsheetImportService.importWithMappings(id, fileBytes, mappings);
        return ResponseEntity.ok(Map.of("createdItems", created));
    }

    @Operation(summary = "Suggest answer for item")
    @PostMapping("/{id}/items/{itemId}/suggest")
    public ResponseEntity<?> suggest(@PathVariable UUID id, @PathVariable UUID itemId) {
        AnswerSuggestion suggestion = questionnairesService.suggestAnswer(id, itemId);
        return ResponseEntity.ok(Map.of(
                "suggestionId", suggestion.getId(),
                "answerText", suggestion.getAnswerText(),
                "citations", suggestion.getCitations() != null ? suggestion.getCitations() : "",
                "confidence", suggestion.getConfidence(),
                "coverageStatus", suggestion.getCoverageStatus().name()
        ));
    }

    @Operation(summary = "Save response for item")
    @PostMapping("/{id}/items/{itemId}/response")
    public ResponseEntity<?> saveResponse(@PathVariable UUID id, @PathVariable UUID itemId,
                                           @RequestBody Map<String, String> body) {
        com.secfix.todos.enums.ResponseStatus status = com.secfix.todos.enums.ResponseStatus.valueOf(body.getOrDefault("status", "DRAFT"));
        QuestionnaireResponse resp = questionnairesService.saveResponse(
                id, itemId, body.get("answerText"), body.get("explanation"), status);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Complete questionnaire")
    @PostMapping("/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        boolean importAnswers = Boolean.TRUE.equals(body.get("importAnswersToLibrary"));
        questionnairesService.complete(id, importAnswers);
        return ResponseEntity.ok(Map.of("status", "COMPLETED"));
    }
}
