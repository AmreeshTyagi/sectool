package com.secfix.todos.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    private static final Logger logger = LoggerFactory.getLogger(ChunkingService.class);
    private static final int MAX_CHUNK_CHARS = 2000;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record ChunkResult(int index, String text, String metadata) {}

    /**
     * Chunks content using structural information when available.
     * For spreadsheets/tables, creates row-based chunks with column context.
     * For text documents, uses paragraph-based chunking.
     */
    public List<ChunkResult> chunk(String extractedText, String mimeType, String parsedJson) {
        if (parsedJson != null) {
            List<ChunkResult> structured = chunkFromStructuredData(parsedJson);
            if (!structured.isEmpty()) return structured;
        }
        return chunkPlainText(extractedText);
    }

    public List<ChunkResult> chunk(String extractedText, String mimeType) {
        return chunkPlainText(extractedText);
    }

    private List<ChunkResult> chunkFromStructuredData(String parsedJson) {
        try {
            JsonNode root = objectMapper.readTree(parsedJson);
            JsonNode tables = root.get("tables");
            if (tables == null || !tables.isArray() || tables.isEmpty()) return List.of();

            List<ChunkResult> chunks = new ArrayList<>();
            int chunkIndex = 0;

            for (int t = 0; t < tables.size(); t++) {
                JsonNode table = tables.get(t);
                String sheetName = table.has("title") ? table.get("title").asText("Sheet") : "Sheet " + (t + 1);
                JsonNode rows = table.get("rows");
                if (rows == null || !rows.isArray() || rows.size() < 2) continue;

                List<String> headers = new ArrayList<>();
                for (JsonNode cell : rows.get(0)) {
                    headers.add(cell.isNull() ? "" : cell.asText().trim());
                }

                StringBuilder currentChunk = new StringBuilder();
                int chunkStartRow = 1;
                String lastCategory = "";

                for (int r = 1; r < rows.size(); r++) {
                    JsonNode row = rows.get(r);
                    StringBuilder rowText = new StringBuilder();

                    String category = "";
                    for (int c = 0; c < Math.min(row.size(), headers.size()); c++) {
                        String cellVal = row.get(c).isNull() ? "" : row.get(c).asText().trim();
                        if (cellVal.isEmpty()) continue;
                        String header = c < headers.size() ? headers.get(c) : "Col" + c;

                        if (header.toLowerCase().matches(".*(category|section|domain|group).*") && !cellVal.isEmpty()) {
                            category = cellVal;
                        }
                        rowText.append(header).append(": ").append(cellVal).append("\n");
                    }

                    if (!category.isEmpty()) lastCategory = category;
                    if (rowText.isEmpty()) continue;

                    String rowWithContext = "[" + sheetName + "]" +
                            (!lastCategory.isEmpty() ? " [" + lastCategory + "]" : "") +
                            " Row " + r + "\n" + rowText + "\n";

                    if (currentChunk.length() + rowWithContext.length() > MAX_CHUNK_CHARS && !currentChunk.isEmpty()) {
                        String meta = buildChunkMetadata(chunkIndex, sheetName, t, chunkStartRow, r - 1, lastCategory);
                        chunks.add(new ChunkResult(chunkIndex++, currentChunk.toString().trim(), meta));
                        currentChunk = new StringBuilder();
                        chunkStartRow = r;
                    }
                    currentChunk.append(rowWithContext);
                }

                if (!currentChunk.isEmpty()) {
                    String meta = buildChunkMetadata(chunkIndex, sheetName, t, chunkStartRow, rows.size() - 1, lastCategory);
                    chunks.add(new ChunkResult(chunkIndex++, currentChunk.toString().trim(), meta));
                }
            }

            if (!chunks.isEmpty()) {
                logger.info("Created {} format-aware chunks from structured data", chunks.size());
            }
            return chunks;
        } catch (Exception e) {
            logger.warn("Failed to chunk from structured data, falling back to plain text", e);
            return List.of();
        }
    }

    private String buildChunkMetadata(int chunkIndex, String sheetName, int sheetIndex,
                                       int startRow, int endRow, String category) {
        try {
            return objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("chunkIndex", chunkIndex);
                put("sheet", sheetName);
                put("sheetIndex", sheetIndex);
                put("startRow", startRow);
                put("endRow", endRow);
                if (category != null && !category.isEmpty()) put("category", category);
                put("type", "structured_table");
            }});
        } catch (Exception e) {
            return "{\"chunkIndex\":" + chunkIndex + "}";
        }
    }

    private List<ChunkResult> chunkPlainText(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) return List.of();

        List<ChunkResult> chunks = new ArrayList<>();
        String[] paragraphs = extractedText.split("\\n\\n+");
        StringBuilder current = new StringBuilder();
        int index = 0;

        for (String para : paragraphs) {
            if (current.length() + para.length() > MAX_CHUNK_CHARS && !current.isEmpty()) {
                chunks.add(new ChunkResult(index++, current.toString().trim(),
                        "{\"chunkIndex\":" + (index - 1) + ",\"type\":\"plain_text\"}"));
                current = new StringBuilder();
            }
            current.append(para).append("\n\n");
        }

        if (!current.isEmpty()) {
            chunks.add(new ChunkResult(index, current.toString().trim(),
                    "{\"chunkIndex\":" + index + ",\"type\":\"plain_text\"}"));
        }
        return chunks;
    }
}

