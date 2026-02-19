package com.secfix.todos.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class SpreadsheetParsingService {
    private static final Logger logger = LoggerFactory.getLogger(SpreadsheetParsingService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record SheetData(String title, List<List<String>> rows) {}

    /**
     * Parses an xlsx file into structured sheet data with all rows and cells.
     */
    public List<SheetData> parseXlsx(byte[] fileBytes) {
        List<SheetData> sheets = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String name = sheet.getSheetName();
                List<List<String>> rows = new ArrayList<>();

                int maxCols = 0;
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row != null) maxCols = Math.max(maxCols, row.getLastCellNum());
                }

                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    List<String> cells = new ArrayList<>();
                    for (int c = 0; c < maxCols; c++) {
                        Cell cell = row != null ? row.getCell(c) : null;
                        cells.add(getCellValue(cell));
                    }
                    if (cells.stream().allMatch(String::isEmpty)) continue;
                    rows.add(cells);
                }
                sheets.add(new SheetData(name, rows));
            }
        } catch (Exception e) {
            logger.error("Failed to parse xlsx file", e);
        }
        return sheets;
    }

    /**
     * Builds a full JSON string with the structured table data merged with Kreuzberg text content.
     */
    public String buildStructuredJson(String kreuzbergContent, String kreuzbergMetadata,
                                       List<SheetData> sheets) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("content", kreuzbergContent != null ? kreuzbergContent : "");

            if (kreuzbergMetadata != null) {
                try {
                    root.set("metadata", objectMapper.readTree(kreuzbergMetadata));
                } catch (Exception e) {
                    root.put("metadata", kreuzbergMetadata);
                }
            }

            ArrayNode tablesArray = objectMapper.createArrayNode();
            for (SheetData sd : sheets) {
                ObjectNode tNode = objectMapper.createObjectNode();
                tNode.put("title", sd.title());
                ArrayNode rowsArr = objectMapper.createArrayNode();
                for (List<String> row : sd.rows()) {
                    ArrayNode rowArr = objectMapper.createArrayNode();
                    for (String cell : row) rowArr.add(cell);
                    rowsArr.add(rowArr);
                }
                tNode.set("rows", rowsArr);
                tablesArray.add(tNode);
            }
            root.set("tables", tablesArray);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.error("Failed to build structured JSON", e);
            return "{}";
        }
    }

    /**
     * Builds rich HTML with tabbed sheets from structured data.
     */
    public String buildRichHtml(List<SheetData> sheets, String filename) {
        if (sheets.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>").append(esc(filename)).append("</title>");
        sb.append("<style>").append(getStyles()).append("</style></head><body>");
        sb.append("<div class='header'><h2>").append(esc(filename)).append("</h2></div>");
        sb.append("<div class='tabs'>");
        for (int i = 0; i < sheets.size(); i++) {
            String active = i == 0 ? " active" : "";
            sb.append("<button class='tab").append(active).append("' onclick='showTab(").append(i).append(")'>")
                    .append(esc(sheets.get(i).title())).append("</button>");
        }
        sb.append("</div>");

        for (int i = 0; i < sheets.size(); i++) {
            String display = i == 0 ? "block" : "none";
            SheetData sd = sheets.get(i);
            sb.append("<div class='sheet' id='sheet-").append(i).append("' style='display:").append(display).append("'>");

            if (sd.rows().isEmpty()) {
                sb.append("<p class='empty'>Empty sheet</p>");
            } else {
                sb.append("<table><thead><tr>");
                for (String cell : sd.rows().get(0)) {
                    sb.append("<th>").append(esc(cell)).append("</th>");
                }
                sb.append("</tr></thead><tbody>");
                for (int r = 1; r < sd.rows().size(); r++) {
                    sb.append("<tr>");
                    for (String cell : sd.rows().get(r)) {
                        sb.append("<td>").append(esc(cell)).append("</td>");
                    }
                    sb.append("</tr>");
                }
                sb.append("</tbody></table>");
            }
            sb.append("</div>");
        }

        sb.append("<script>function showTab(idx){");
        sb.append("document.querySelectorAll('.sheet').forEach(s=>s.style.display='none');");
        sb.append("document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));");
        sb.append("document.getElementById('sheet-'+idx).style.display='block';");
        sb.append("document.querySelectorAll('.tab')[idx].classList.add('active');");
        sb.append("}</script></body></html>");
        return sb.toString();
    }

    private String getStyles() {
        return "body{margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f8f9fa;color:#333;}" +
                ".header{padding:16px 24px;background:#fff;border-bottom:1px solid #e0e0e0;}" +
                ".header h2{margin:0;font-size:18px;font-weight:600;}" +
                ".tabs{display:flex;gap:0;background:#fff;border-bottom:2px solid #e0e0e0;padding:0 16px;}" +
                ".tab{padding:10px 20px;border:none;background:none;cursor:pointer;font-size:13px;font-weight:500;color:#666;" +
                "border-bottom:2px solid transparent;margin-bottom:-2px;transition:all .2s;}" +
                ".tab:hover{color:#333;background:#f0f0f0;}" +
                ".tab.active{color:#6c63ff;border-bottom-color:#6c63ff;font-weight:600;}" +
                ".sheet{padding:16px 24px;overflow-x:auto;}" +
                "table{border-collapse:collapse;width:100%;font-size:12px;background:#fff;border-radius:6px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.08);}" +
                "th{background:#f5f5f5;font-weight:600;text-align:left;padding:8px 12px;border:1px solid #e0e0e0;white-space:nowrap;}" +
                "td{padding:6px 12px;border:1px solid #e8e8e8;vertical-align:top;max-width:400px;word-wrap:break-word;}" +
                "tr:nth-child(even){background:#fafafa;}" +
                "tr:hover{background:#f0f4ff;}" +
                ".empty{color:#999;font-style:italic;padding:24px;}";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getLocalDateTimeCellValue().toString();
                    }
                    double v = cell.getNumericCellValue();
                    yield v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e) {
                        try {
                            yield cell.getStringCellValue();
                        } catch (Exception e2) {
                            yield "";
                        }
                    }
                }
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }
}
