package com.secfix.todos.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class KreuzbergClient {
    private static final Logger logger = LoggerFactory.getLogger(KreuzbergClient.class);
    private final String baseUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record TableData(String title, List<List<String>> rows) {}

    public record KreuzbergResult(
            String extractedText,
            String fullJson,
            String renderedHtml,
            List<TableData> tables
    ) {}

    public KreuzbergClient(@Value("${sectool.kreuzberg.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public KreuzbergResult parse(byte[] fileBytes, String filename, String mimeType) {
        try {
            String boundary = UUID.randomUUID().toString();
            byte[] body = buildMultipartBody(boundary, fileBytes, filename, mimeType);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/extract"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Kreuzberg parse failed: status={}, body={}", response.statusCode(), response.body());
                throw new RuntimeException("Kreuzberg parse failed with status " + response.statusCode());
            }

            JsonNode results = objectMapper.readTree(response.body());
            JsonNode first = results.isArray() && !results.isEmpty() ? results.get(0) : results;

            String text = first.has("content") ? first.get("content").asText("") : "";
            List<TableData> tables = extractTables(first);
            String fullJson = buildFullJson(first, tables);
            String html = buildRichHtml(text, tables, filename);

            return new KreuzbergResult(text, fullJson, html, tables);
        } catch (Exception e) {
            logger.error("Failed to call kreuzberg service", e);
            throw new RuntimeException("Kreuzberg parsing failed: " + e.getMessage(), e);
        }
    }

    private List<TableData> extractTables(JsonNode result) {
        List<TableData> tables = new ArrayList<>();
        JsonNode tablesNode = result.get("tables");
        if (tablesNode == null || !tablesNode.isArray()) return tables;

        int idx = 0;
        for (JsonNode table : tablesNode) {
            String title = table.has("title") && !table.get("title").isNull()
                    ? table.get("title").asText() : "Sheet " + (idx + 1);

            List<List<String>> rows = new ArrayList<>();
            JsonNode rowsNode = table.has("rows") ? table.get("rows") : null;
            if (rowsNode != null && rowsNode.isArray()) {
                for (JsonNode row : rowsNode) {
                    List<String> cells = new ArrayList<>();
                    if (row.isArray()) {
                        for (JsonNode cell : row) {
                            cells.add(cell.isNull() ? "" : cell.asText());
                        }
                    }
                    rows.add(cells);
                }
            }
            tables.add(new TableData(title, rows));
            idx++;
        }
        return tables;
    }

    private String buildFullJson(JsonNode original, List<TableData> tables) {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("content", original.has("content") ? original.get("content").asText("") : "");
            if (original.has("metadata")) result.set("metadata", original.get("metadata"));
            if (original.has("mime_type")) result.put("mime_type", original.get("mime_type").asText());

            ArrayNode tablesArray = objectMapper.createArrayNode();
            for (TableData td : tables) {
                ObjectNode tNode = objectMapper.createObjectNode();
                tNode.put("title", td.title());
                ArrayNode rowsArr = objectMapper.createArrayNode();
                for (List<String> row : td.rows()) {
                    ArrayNode rowArr = objectMapper.createArrayNode();
                    for (String cell : row) rowArr.add(cell);
                    rowsArr.add(rowArr);
                }
                tNode.set("rows", rowsArr);
                tablesArray.add(tNode);
            }
            result.set("tables", tablesArray);

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.warn("Failed to build full JSON, using raw content", e);
            return original.toString();
        }
    }

    private String buildRichHtml(String text, List<TableData> tables, String filename) {
        if (tables.isEmpty()) {
            return buildTextHtml(text);
        }
        return buildTabbedHtml(tables, filename);
    }

    private String buildTextHtml(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                "<style>" + getBaseStyles() + " pre{white-space:pre-wrap;word-wrap:break-word;padding:20px;font-size:13px;line-height:1.6;}" +
                "</style></head><body><pre>" + escaped + "</pre></body></html>";
    }

    private String buildTabbedHtml(List<TableData> tables, String filename) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>").append(esc(filename)).append("</title>");
        sb.append("<style>").append(getBaseStyles()).append(getTabbedStyles()).append("</style></head><body>");
        sb.append("<div class='header'><h2>").append(esc(filename)).append("</h2></div>");
        sb.append("<div class='tabs'>");
        for (int i = 0; i < tables.size(); i++) {
            String active = i == 0 ? " active" : "";
            sb.append("<button class='tab").append(active).append("' onclick='showTab(").append(i).append(")'>")
                    .append(esc(tables.get(i).title())).append("</button>");
        }
        sb.append("</div>");

        for (int i = 0; i < tables.size(); i++) {
            String display = i == 0 ? "block" : "none";
            TableData td = tables.get(i);
            sb.append("<div class='sheet' id='sheet-").append(i).append("' style='display:").append(display).append("'>");

            if (td.rows().isEmpty()) {
                sb.append("<p class='empty'>Empty sheet</p>");
            } else {
                sb.append("<table><thead>");
                if (!td.rows().isEmpty()) {
                    sb.append("<tr>");
                    for (String cell : td.rows().get(0)) {
                        sb.append("<th>").append(esc(cell)).append("</th>");
                    }
                    sb.append("</tr></thead><tbody>");
                    for (int r = 1; r < td.rows().size(); r++) {
                        sb.append("<tr>");
                        for (String cell : td.rows().get(r)) {
                            sb.append("<td>").append(esc(cell)).append("</td>");
                        }
                        sb.append("</tr>");
                    }
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

    private String getBaseStyles() {
        return "body{margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f8f9fa;color:#333;}" +
                ".header{padding:16px 24px;background:#fff;border-bottom:1px solid #e0e0e0;}" +
                ".header h2{margin:0;font-size:18px;font-weight:600;}";
    }

    private String getTabbedStyles() {
        return ".tabs{display:flex;gap:0;background:#fff;border-bottom:2px solid #e0e0e0;padding:0 16px;}" +
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

    private byte[] buildMultipartBody(String boundary, byte[] fileBytes, String filename, String mimeType) {
        String header = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"files\"; filename=\"" + filename + "\"\r\n" +
                "Content-Type: " + mimeType + "\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";

        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);
        return body;
    }
}
