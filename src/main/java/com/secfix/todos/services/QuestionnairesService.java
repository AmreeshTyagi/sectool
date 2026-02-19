package com.secfix.todos.services;

import com.secfix.todos.database.models.*;
import com.secfix.todos.database.repositories.*;
import com.secfix.todos.enums.*;
import com.secfix.todos.exceptions.ApiServiceCallException;
import com.secfix.todos.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class QuestionnairesService {

    private final QuestionnaireRepository questionnaireRepo;
    private final QuestionnaireItemRepository itemRepo;
    private final AnswerSuggestionRepository suggestionRepo;
    private final QuestionnaireResponseRepository responseRepo;
    private final AnswerFeedbackRepository feedbackRepo;
    private final AnswerLibraryEntryRepository answerLibraryRepo;
    private final RagService ragService;

    public QuestionnairesService(QuestionnaireRepository questionnaireRepo,
                                  QuestionnaireItemRepository itemRepo,
                                  AnswerSuggestionRepository suggestionRepo,
                                  QuestionnaireResponseRepository responseRepo,
                                  AnswerFeedbackRepository feedbackRepo,
                                  AnswerLibraryEntryRepository answerLibraryRepo,
                                  RagService ragService) {
        this.questionnaireRepo = questionnaireRepo;
        this.itemRepo = itemRepo;
        this.suggestionRepo = suggestionRepo;
        this.responseRepo = responseRepo;
        this.feedbackRepo = feedbackRepo;
        this.answerLibraryRepo = answerLibraryRepo;
        this.ragService = ragService;
    }

    public Questionnaire create(String name, QuestionnaireType type, LocalDate dueDate, Integer ownerUserId) {
        UUID tenantId = TenantContext.getTenantId();
        Questionnaire q = new Questionnaire();
        q.setTenantId(tenantId);
        q.setName(name);
        q.setType(type);
        q.setStatus(QuestionnaireStatus.IN_PROGRESS);
        q.setProgressPercent(0);
        q.setDueDate(dueDate);
        q.setOwnerUserId(ownerUserId);
        return questionnaireRepo.save(q);
    }

    public List<Questionnaire> list(QuestionnaireStatus status) {
        UUID tenantId = TenantContext.getTenantId();
        if (status != null) {
            return questionnaireRepo.findByTenantIdAndStatus(tenantId, status);
        }
        return questionnaireRepo.findByTenantId(tenantId);
    }

    public Questionnaire getById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return questionnaireRepo.findById(id)
                .filter(q -> q.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ApiServiceCallException("Questionnaire not found", HttpStatus.NOT_FOUND));
    }

    public List<QuestionnaireItem> getItems(UUID questionnaireId) {
        UUID tenantId = TenantContext.getTenantId();
        return itemRepo.findByTenantIdAndQuestionnaireIdOrderByItemIndex(tenantId, questionnaireId);
    }

    public QuestionnaireItem createItem(UUID questionnaireId, int index, String questionText, ResponseType responseType) {
        UUID tenantId = TenantContext.getTenantId();
        QuestionnaireItem item = new QuestionnaireItem();
        item.setTenantId(tenantId);
        item.setQuestionnaireId(questionnaireId);
        item.setItemIndex(index);
        item.setQuestionText(questionText);
        item.setResponseType(responseType);
        item.setCurrentState(QuestionnaireItemState.UNANSWERED);
        return itemRepo.save(item);
    }

    public AnswerSuggestion suggestAnswer(UUID questionnaireId, UUID itemId) {
        UUID tenantId = TenantContext.getTenantId();
        QuestionnaireItem item = itemRepo.findById(itemId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ApiServiceCallException("Item not found", HttpStatus.NOT_FOUND));

        RagService.SuggestionResult result = ragService.suggest(item.getQuestionText(), tenantId);

        AnswerSuggestion suggestion = new AnswerSuggestion();
        suggestion.setTenantId(tenantId);
        suggestion.setQuestionnaireItemId(itemId);
        suggestion.setProvider("configured");
        suggestion.setModel("default");
        suggestion.setAnswerText(result.answerText());
        try {
            suggestion.setCitations(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(result.citations()));
        } catch (Exception e) {
            suggestion.setCitations("[]");
        }
        suggestion.setConfidence(result.confidence());
        suggestion.setCoverageStatus(result.coverageStatus());
        suggestion = suggestionRepo.save(suggestion);

        item.setCurrentState(QuestionnaireItemState.SUGGESTED);
        itemRepo.save(item);

        updateProgress(questionnaireId);
        return suggestion;
    }

    public QuestionnaireResponse saveResponse(UUID questionnaireId, UUID itemId,
                                               String answerText, String explanation, ResponseStatus status) {
        UUID tenantId = TenantContext.getTenantId();
        QuestionnaireItem item = itemRepo.findById(itemId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ApiServiceCallException("Item not found", HttpStatus.NOT_FOUND));

        QuestionnaireResponse resp = new QuestionnaireResponse();
        resp.setTenantId(tenantId);
        resp.setQuestionnaireItemId(itemId);
        resp.setAnswerText(answerText);
        resp.setExplanation(explanation);
        resp.setStatus(status);
        resp.setCreatedBy(TenantContext.getUserId());
        if (status == ResponseStatus.APPROVED) {
            resp.setApprovedBy(TenantContext.getUserId());
            resp.setApprovedAt(Instant.now());
            item.setCurrentState(QuestionnaireItemState.APPROVED);
        } else {
            item.setCurrentState(QuestionnaireItemState.DRAFTED);
        }
        itemRepo.save(item);
        resp = responseRepo.save(resp);
        updateProgress(questionnaireId);
        return resp;
    }

    public void complete(UUID questionnaireId, boolean importAnswersToLibrary) {
        UUID tenantId = TenantContext.getTenantId();
        Questionnaire q = getById(questionnaireId);
        q.setStatus(QuestionnaireStatus.COMPLETED);
        questionnaireRepo.save(q);

        if (importAnswersToLibrary) {
            List<QuestionnaireItem> items = getItems(questionnaireId);
            for (QuestionnaireItem item : items) {
                List<QuestionnaireResponse> responses = responseRepo.findByTenantIdAndQuestionnaireItemId(tenantId, item.getId());
                for (QuestionnaireResponse resp : responses) {
                    if (resp.getStatus() == ResponseStatus.APPROVED) {
                        AnswerLibraryEntry entry = new AnswerLibraryEntry();
                        entry.setTenantId(tenantId);
                        entry.setQuestionText(item.getQuestionText());
                        entry.setQuestionNormalized(item.getQuestionText().trim().toLowerCase());
                        entry.setAnswerText(resp.getAnswerText());
                        entry.setExplanation(resp.getExplanation());
                        entry.setSource(AnswerSource.IMPORTED);
                        entry.setCreatedBy(TenantContext.getUserId());
                        answerLibraryRepo.save(entry);
                    }
                }
            }
        }
    }

    public AnswerFeedback saveFeedback(UUID suggestionId, FeedbackThumb thumb, String comment) {
        UUID tenantId = TenantContext.getTenantId();
        AnswerFeedback feedback = new AnswerFeedback();
        feedback.setTenantId(tenantId);
        feedback.setAnswerSuggestionId(suggestionId);
        feedback.setThumb(thumb);
        feedback.setComment(comment);
        feedback.setCreatedBy(TenantContext.getUserId());
        return feedbackRepo.save(feedback);
    }

    public long countPendingImportAnswers() {
        UUID tenantId = TenantContext.getTenantId();
        return responseRepo.findByTenantIdAndStatus(tenantId, ResponseStatus.APPROVED).stream()
                .filter(r -> !isAlreadyInLibrary(tenantId, r.getQuestionnaireItemId()))
                .count();
    }

    public record PendingAnswer(UUID responseId, UUID questionnaireItemId, String questionText,
                                 String answerText, String explanation, String questionnaireName) {}

    public List<PendingAnswer> listPendingImportAnswers() {
        UUID tenantId = TenantContext.getTenantId();
        List<QuestionnaireResponse> approved = responseRepo.findByTenantIdAndStatus(tenantId, ResponseStatus.APPROVED);
        List<PendingAnswer> result = new java.util.ArrayList<>();
        for (QuestionnaireResponse resp : approved) {
            QuestionnaireItem item = itemRepo.findById(resp.getQuestionnaireItemId()).orElse(null);
            if (item == null) continue;
            if (isAlreadyInLibrary(tenantId, item.getQuestionText())) continue;
            String qName = questionnaireRepo.findById(item.getQuestionnaireId())
                    .map(Questionnaire::getName).orElse("Unknown");
            result.add(new PendingAnswer(resp.getId(), item.getId(), item.getQuestionText(),
                    resp.getAnswerText(), resp.getExplanation(), qName));
        }
        return result;
    }

    public int importAnswersToLibrary(List<UUID> responseIds) {
        UUID tenantId = TenantContext.getTenantId();
        int imported = 0;
        for (UUID responseId : responseIds) {
            QuestionnaireResponse resp = responseRepo.findById(responseId)
                    .filter(r -> r.getTenantId().equals(tenantId) && r.getStatus() == ResponseStatus.APPROVED)
                    .orElse(null);
            if (resp == null) continue;
            QuestionnaireItem item = itemRepo.findById(resp.getQuestionnaireItemId()).orElse(null);
            if (item == null) continue;

            AnswerLibraryEntry entry = new AnswerLibraryEntry();
            entry.setTenantId(tenantId);
            entry.setQuestionText(item.getQuestionText());
            entry.setQuestionNormalized(item.getQuestionText().trim().toLowerCase());
            entry.setAnswerText(resp.getAnswerText());
            entry.setExplanation(resp.getExplanation());
            entry.setSource(AnswerSource.IMPORTED);
            entry.setCreatedBy(TenantContext.getUserId());
            answerLibraryRepo.save(entry);
            imported++;
        }
        return imported;
    }

    private boolean isAlreadyInLibrary(UUID tenantId, UUID itemId) {
        QuestionnaireItem item = itemRepo.findById(itemId).orElse(null);
        if (item == null) return true;
        return isAlreadyInLibrary(tenantId, item.getQuestionText());
    }

    private boolean isAlreadyInLibrary(UUID tenantId, String questionText) {
        String normalized = questionText.trim().toLowerCase();
        return answerLibraryRepo.findByTenantId(tenantId).stream()
                .anyMatch(e -> normalized.equals(e.getQuestionNormalized()));
    }

    private void updateProgress(UUID questionnaireId) {
        UUID tenantId = TenantContext.getTenantId();
        List<QuestionnaireItem> items = itemRepo.findByTenantIdAndQuestionnaireIdOrderByItemIndex(tenantId, questionnaireId);
        if (items.isEmpty()) return;
        long answered = items.stream().filter(i ->
                i.getCurrentState() == QuestionnaireItemState.APPROVED ||
                i.getCurrentState() == QuestionnaireItemState.DRAFTED).count();
        int progress = (int) ((answered * 100) / items.size());
        Questionnaire q = questionnaireRepo.findById(questionnaireId).orElse(null);
        if (q != null) {
            q.setProgressPercent(progress);
            questionnaireRepo.save(q);
        }
    }
}
