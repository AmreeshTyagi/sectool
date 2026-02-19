package com.secfix.todos.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secfix.todos.database.models.*;
import com.secfix.todos.database.repositories.*;
import com.secfix.todos.enums.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class QuestionnaireExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(QuestionnaireExtractionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final QuestionnaireRepository questionnaireRepo;
    private final QuestionnaireItemRepository itemRepo;
    private final QuestionnaireResponseRepository responseRepo;

    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "(?i)(question|item|specifics|description|requirement|control|query|ask|detail)");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile(
            "(?i)(category|section|domain|area|group|topic)");
    private static final Pattern ANSWER_PATTERN = Pattern.compile(
            "(?i)(answer|response|vendor.*(comment|response|note)|comment|note|evidence)");
    private static final Pattern SCORE_PATTERN = Pattern.compile(
            "(?i)(score|rating|weighting|weight|vendor.*supplied|rag)");

    public QuestionnaireExtractionService(
            QuestionnaireRepository questionnaireRepo,
            QuestionnaireItemRepository itemRepo,
            QuestionnaireResponseRepository responseRepo) {
        this.questionnaireRepo = questionnaireRepo;
        this.itemRepo = itemRepo;
        this.responseRepo = responseRepo;
    }

    public record ExtractionResult(UUID questionnaireId, int itemsCreated, String sheetName) {}

    public ExtractionResult extract(UUID tenantId, UUID documentVersionId,
                                     String documentTitle, String parsedJson) {
        try {
            JsonNode root = objectMapper.readTree(parsedJson);
            JsonNode tablesNode = root.get("tables");
            if (tablesNode == null || !tablesNode.isArray() || tablesNode.isEmpty()) {
                logger.info("No tables found in parsed JSON, skipping extraction");
                return null;
            }

            List<SheetAnalysis> allSheets = findAllQuestionSheets(tablesNode);
            if (allSheets.isEmpty()) {
                logger.info("No suitable question sheets detected in document");
                return null;
            }

            Questionnaire questionnaire = new Questionnaire();
            questionnaire.setTenantId(tenantId);
            questionnaire.setName(documentTitle);
            questionnaire.setType(QuestionnaireType.SPREADSHEET);
            questionnaire.setStatus(QuestionnaireStatus.IN_PROGRESS);
            questionnaire.setProgressPercent(0);
            questionnaire.setSourceDocumentVersionId(documentVersionId);
            questionnaire = questionnaireRepo.save(questionnaire);

            int itemIndex = 0;
            List<String> processedSheets = new ArrayList<>();

            for (SheetAnalysis sheet : allSheets) {
                String lastCategory = "";
                int sheetItems = 0;

                for (int r = sheet.dataStartRow; r < sheet.rows.size(); r++) {
                    List<String> row = sheet.rows.get(r);
                    String questionText = safeGet(row, sheet.questionCol);
                    String itemText = sheet.itemCol >= 0 ? safeGet(row, sheet.itemCol) : "";
                    if (questionText.isBlank() && itemText.isBlank()) continue;
                    if (questionText.isBlank()) questionText = itemText;

                    String category = sheet.categoryCol >= 0 ? safeGet(row, sheet.categoryCol) : "";
                    if (!category.isBlank()) lastCategory = category;

                    String answerText = sheet.answerCol >= 0 ? safeGet(row, sheet.answerCol) : "";

                    String sourceLocation = objectMapper.writeValueAsString(Map.of(
                            "sheet", sheet.sheetName,
                            "row", r,
                            "category", lastCategory,
                            "questionCol", sheet.questionCol,
                            "answerCol", sheet.answerCol
                    ));

                    QuestionnaireItem item = new QuestionnaireItem();
                    item.setTenantId(tenantId);
                    item.setQuestionnaireId(questionnaire.getId());
                    item.setItemIndex(itemIndex++);
                    String fullQuestion = questionText;
                    if (!itemText.isBlank() && !itemText.equals(questionText)) {
                        fullQuestion = itemText + " — " + questionText;
                    }
                    if (!lastCategory.isBlank()) {
                        fullQuestion = "[" + lastCategory + "] " + fullQuestion;
                    }
                    item.setQuestionText(fullQuestion);
                    item.setResponseType(ResponseType.FREE_TEXT);
                    item.setCurrentState(answerText.isBlank()
                            ? QuestionnaireItemState.UNANSWERED
                            : QuestionnaireItemState.DRAFTED);
                    item.setSourceLocation(sourceLocation);
                    item = itemRepo.save(item);

                    if (!answerText.isBlank()) {
                        QuestionnaireResponse resp = new QuestionnaireResponse();
                        resp.setTenantId(tenantId);
                        resp.setQuestionnaireItemId(item.getId());
                        resp.setAnswerText(answerText);
                        resp.setStatus(ResponseStatus.DRAFT);
                        responseRepo.save(resp);
                    }
                    sheetItems++;
                }

                if (sheetItems > 0) {
                    processedSheets.add(sheet.sheetName + "(" + sheetItems + ")");
                }
            }

            if (itemIndex > 0) {
                long answered = itemRepo.findByTenantIdAndQuestionnaireIdOrderByItemIndex(tenantId, questionnaire.getId())
                        .stream().filter(i -> i.getCurrentState() != QuestionnaireItemState.UNANSWERED).count();
                questionnaire.setProgressPercent((int) ((answered * 100) / itemIndex));
                questionnaireRepo.save(questionnaire);
            }

            String sheetsInfo = String.join(", ", processedSheets);
            logger.info("Extracted {} items from {} sheets [{}] into questionnaire {}",
                    itemIndex, processedSheets.size(), sheetsInfo, questionnaire.getId());
            return new ExtractionResult(questionnaire.getId(), itemIndex, sheetsInfo);
        } catch (Exception e) {
            logger.error("Questionnaire extraction failed", e);
            return null;
        }
    }

    private record SheetAnalysis(
            String sheetName,
            List<List<String>> rows,
            int dataStartRow,
            int questionCol,
            int categoryCol,
            int answerCol,
            int scoreCol,
            double confidence,
            int itemCol
    ) {}

    private List<SheetAnalysis> findAllQuestionSheets(JsonNode tablesNode) {
        List<SheetAnalysis> results = new ArrayList<>();

        for (int t = 0; t < tablesNode.size(); t++) {
            JsonNode table = tablesNode.get(t);
            String title = table.has("title") && !table.get("title").isNull()
                    ? table.get("title").asText() : "Sheet " + (t + 1);
            JsonNode rowsNode = table.get("rows");
            if (rowsNode == null || !rowsNode.isArray() || rowsNode.size() < 2) continue;

            List<List<String>> rows = new ArrayList<>();
            for (JsonNode row : rowsNode) {
                List<String> cells = new ArrayList<>();
                if (row.isArray()) {
                    for (JsonNode cell : row) cells.add(cell.isNull() ? "" : cell.asText());
                }
                rows.add(cells);
            }

            SheetAnalysis bestForSheet = null;
            for (int headerRow = 0; headerRow < Math.min(rows.size(), 15); headerRow++) {
                SheetAnalysis analysis = analyzeHeaderRow(title, rows, headerRow);
                if (analysis != null && (bestForSheet == null || analysis.confidence > bestForSheet.confidence)) {
                    bestForSheet = analysis;
                }
            }

            if (bestForSheet != null) {
                results.add(bestForSheet);
                logger.info("Sheet '{}' qualifies with confidence {}, questionCol={}, answerCol={}, {} data rows",
                        title, String.format("%.2f", bestForSheet.confidence),
                        bestForSheet.questionCol, bestForSheet.answerCol,
                        bestForSheet.rows.size() - bestForSheet.dataStartRow);
            }
        }

        results.sort(Comparator.comparingDouble(SheetAnalysis::confidence).reversed());
        return results;
    }

    private SheetAnalysis analyzeHeaderRow(String sheetName, List<List<String>> rows, int headerRow) {
        List<String> headers = rows.get(headerRow);
        int questionCol = -1, itemCol = -1, categoryCol = -1, answerCol = -1, scoreCol = -1;
        double confidence = 0;
        int matchCount = 0;

        for (int c = 0; c < headers.size(); c++) {
            String h = headers.get(c).trim();
            if (h.isEmpty()) continue;

            if (QUESTION_PATTERN.matcher(h).find()) {
                if (h.toLowerCase().matches(".*specifics.*") || questionCol < 0) {
                    if (questionCol >= 0) itemCol = questionCol;
                    questionCol = c;
                }
                matchCount++;
                confidence += 0.2;
            } else if (CATEGORY_PATTERN.matcher(h).find() && categoryCol < 0) {
                categoryCol = c;
                matchCount++;
                confidence += 0.1;
            } else if (ANSWER_PATTERN.matcher(h).find() && answerCol < 0) {
                answerCol = c;
                matchCount++;
                confidence += 0.3;
            } else if (SCORE_PATTERN.matcher(h).find() && scoreCol < 0) {
                scoreCol = c;
                matchCount++;
                confidence += 0.1;
            }
        }

        if (questionCol < 0) return null;
        if (matchCount < 2) return null;

        if (itemCol < 0 && categoryCol < 0) {
            for (int c = 0; c < questionCol; c++) {
                String h = headers.get(c).trim();
                if (!h.isEmpty() && h.length() < 30) {
                    itemCol = c;
                }
            }
        }

        int dataRows = rows.size() - headerRow - 1;
        if (dataRows < 2) return null;
        confidence += Math.min(dataRows / 50.0, 0.2);

        return new SheetAnalysis(sheetName, rows, headerRow + 1,
                questionCol, categoryCol,
                answerCol, scoreCol, confidence, itemCol >= 0 ? itemCol : -1);
    }

    private String safeGet(List<String> row, int col) {
        return col >= 0 && col < row.size() ? row.get(col).trim() : "";
    }
}
