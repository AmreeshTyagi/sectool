package com.secfix.todos.services;

import com.secfix.todos.database.models.QuestionnaireItem;
import com.secfix.todos.enums.QuestionnaireItemState;
import com.secfix.todos.enums.ResponseType;
import com.secfix.todos.database.repositories.QuestionnaireItemRepository;
import com.secfix.todos.tenancy.TenantContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.*;

@Service
public class SpreadsheetImportService {
    private static final Logger logger = LoggerFactory.getLogger(SpreadsheetImportService.class);

    private final QuestionnaireItemRepository itemRepo;

    public SpreadsheetImportService(QuestionnaireItemRepository itemRepo) {
        this.itemRepo = itemRepo;
    }

    public record ImportPreview(List<String> columns, List<List<String>> rows) {}

    public ImportPreview preview(byte[] fileBytes) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> columns = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();

            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    columns.add(getCellValue(cell));
                }
            }

            int maxRows = Math.min(sheet.getLastRowNum(), 20);
            for (int i = 1; i <= maxRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                List<String> rowData = new ArrayList<>();
                for (int j = 0; j < columns.size(); j++) {
                    Cell cell = row.getCell(j);
                    rowData.add(cell != null ? getCellValue(cell) : "");
                }
                rows.add(rowData);
            }

            return new ImportPreview(columns, rows);
        } catch (Exception e) {
            logger.error("Failed to preview spreadsheet", e);
            throw new RuntimeException("Failed to parse spreadsheet: " + e.getMessage());
        }
    }

    public int importWithMappings(UUID questionnaireId, byte[] fileBytes, Map<String, String> mappings) {
        UUID tenantId = TenantContext.getTenantId();
        int questionCol = -1;
        int answerCol = -1;
        int explanationCol = -1;

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return 0;

            List<String> columns = new ArrayList<>();
            for (Cell cell : headerRow) {
                columns.add(getCellValue(cell));
            }

            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                int colIdx = Integer.parseInt(entry.getKey());
                switch (entry.getValue()) {
                    case "QUESTION" -> questionCol = colIdx;
                    case "ANSWER", "ANSWER_AND_EXPLANATION" -> answerCol = colIdx;
                    case "EXPLANATION" -> explanationCol = colIdx;
                }
                if ("ANSWER_AND_EXPLANATION".equals(entry.getValue())) {
                    answerCol = colIdx;
                }
            }

            if (questionCol == -1) throw new RuntimeException("No question column mapped");

            int created = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell questionCell = row.getCell(questionCol);
                if (questionCell == null) continue;
                String question = getCellValue(questionCell).trim();
                if (question.isEmpty()) continue;

                QuestionnaireItem item = new QuestionnaireItem();
                item.setTenantId(tenantId);
                item.setQuestionnaireId(questionnaireId);
                item.setItemIndex(created);
                item.setQuestionText(question);
                item.setResponseType(ResponseType.FREE_TEXT);
                item.setCurrentState(QuestionnaireItemState.UNANSWERED);

                String sourceLocation = "{\"row\":" + i + ",\"questionCol\":" + questionCol + "}";
                item.setSourceLocation(sourceLocation);

                itemRepo.save(item);
                created++;
            }

            return created;
        } catch (Exception e) {
            logger.error("Failed to import spreadsheet", e);
            throw new RuntimeException("Spreadsheet import failed: " + e.getMessage());
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
