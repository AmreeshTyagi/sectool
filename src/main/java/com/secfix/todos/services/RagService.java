package com.secfix.todos.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secfix.todos.database.models.*;
import com.secfix.todos.database.repositories.*;
import com.secfix.todos.enums.CoverageStatus;
import com.secfix.todos.enums.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagService {
    private static final Logger logger = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_K = 5;
    private static final double MIN_SIMILARITY_THRESHOLD = 0.1;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final KbChunkRepository chunkRepo;
    private final KbEmbeddingRepository embeddingRepo;
    private final AnswerLibraryEntryRepository answerLibraryRepo;
    private final DocumentVersionRepository documentVersionRepo;
    private final DocumentRepository documentRepo;
    private final EmbeddingsClient embeddingsClient;
    private final LlmClient llmClient;

    public record SuggestionResult(String answerText, List<String> citations, double confidence, CoverageStatus coverageStatus) {}

    public RagService(KbChunkRepository chunkRepo, KbEmbeddingRepository embeddingRepo,
                      AnswerLibraryEntryRepository answerLibraryRepo,
                      DocumentVersionRepository documentVersionRepo,
                      DocumentRepository documentRepo,
                      EmbeddingsClient embeddingsClient, LlmClient llmClient) {
        this.chunkRepo = chunkRepo;
        this.embeddingRepo = embeddingRepo;
        this.answerLibraryRepo = answerLibraryRepo;
        this.documentVersionRepo = documentVersionRepo;
        this.documentRepo = documentRepo;
        this.embeddingsClient = embeddingsClient;
        this.llmClient = llmClient;
    }

    public SuggestionResult suggest(String questionText, UUID tenantId) {
        List<AnswerLibraryEntry> libraryEntries = answerLibraryRepo.findByTenantId(tenantId);
        for (AnswerLibraryEntry entry : libraryEntries) {
            if (entry.getQuestionNormalized() != null &&
                entry.getQuestionNormalized().equalsIgnoreCase(questionText.trim())) {
                return new SuggestionResult(entry.getAnswerText(),
                        List.of("answer_library:" + entry.getId()), 0.95, CoverageStatus.OK);
            }
        }

        float[] queryVector = embedQuery(questionText);
        List<ScoredChunk> rankedChunks = retrieveChunks(tenantId, queryVector);

        if (rankedChunks.isEmpty()) {
            return new SuggestionResult(
                    "I don't have enough information in the knowledge base to answer this question. Please upload relevant policies or documents.",
                    List.of(), 0.0, CoverageStatus.INSUFFICIENT_EVIDENCE);
        }

        StringBuilder context = new StringBuilder();
        List<String> citations = new ArrayList<>();
        double topScore = rankedChunks.getFirst().score;

        for (ScoredChunk sc : rankedChunks) {
            context.append("---\nSource: chunk ").append(sc.chunk.getId()).append("\n");
            context.append(sc.chunk.getText()).append("\n");
            citations.add("kb_chunk:" + sc.chunk.getId());
            logger.debug("Chunk {} score={} text={}...", sc.chunk.getId(),
                    String.format("%.4f", sc.score),
                    sc.chunk.getText().substring(0, Math.min(80, sc.chunk.getText().length())));
        }

        String systemPrompt = """
            You are a security questionnaire answering assistant. Answer based on the provided context.

            Rules:
            1. If the context contains ANY relevant evidence, provide an answer using that evidence.
               Start with Yes/No/Partial, then explain what the organization does and cite the sources.
            2. Only say "INSUFFICIENT_EVIDENCE" if the context contains absolutely nothing relevant to the question.
            3. If the context partially covers the question, answer "Partial" or "Yes" based on what IS covered,
               then clearly note which specific aspects are not addressed in the available documentation.
            4. Always cite chunk IDs as sources.
            5. Be concise. Write a direct answer suitable for a questionnaire response.
            """;

        String userPrompt = "Context:\n" + context + "\n\nQuestion: " + questionText;

        String answer = llmClient.complete(systemPrompt, userPrompt);

        String answerLower = answer.toLowerCase().trim();
        boolean noEvidence = answerLower.startsWith("insufficient_evidence")
                || (answerLower.contains("insufficient_evidence") && !answerLower.contains("yes") && !answerLower.contains("partial"));
        CoverageStatus coverage = noEvidence ? CoverageStatus.INSUFFICIENT_EVIDENCE : CoverageStatus.OK;
        double confidence = coverage == CoverageStatus.OK ? Math.min(0.95, topScore) : 0.1;

        return new SuggestionResult(answer, citations, confidence, coverage);
    }

    private float[] embedQuery(String text) {
        List<float[]> result = embeddingsClient.embed(List.of(text));
        if (result.isEmpty()) return new float[0];
        return result.getFirst();
    }

    private record ScoredChunk(KbChunk chunk, double score) {}

    private List<ScoredChunk> retrieveChunks(UUID tenantId, float[] queryVector) {
        List<KbEmbedding> embeddings = embeddingRepo.findByTenantId(tenantId);
        if (embeddings.isEmpty()) {
            logger.warn("No embeddings found for tenant {}", tenantId);
            return List.of();
        }

        Set<UUID> policyVersionIds = buildPolicyVersionIds(tenantId);
        logger.info("Retrieval: {} policy document versions as knowledge sources", policyVersionIds.size());

        Map<UUID, KbChunk> chunkMap = new HashMap<>();
        List<ScoredChunk> scored = new ArrayList<>();

        for (KbEmbedding emb : embeddings) {
            float[] storedVector = parseEmbedding(emb.getEmbedding());
            if (storedVector.length == 0 || isZeroVector(storedVector)) continue;

            double similarity = (queryVector.length > 0 && queryVector.length == storedVector.length)
                    ? cosineSimilarity(queryVector, storedVector)
                    : 0.0;

            if (similarity < MIN_SIMILARITY_THRESHOLD) continue;

            KbChunk chunk = chunkMap.computeIfAbsent(emb.getChunkId(),
                    id -> chunkRepo.findById(id).orElse(null));
            if (chunk == null) continue;

            if (!policyVersionIds.isEmpty() && !policyVersionIds.contains(chunk.getDocumentVersionId())) {
                continue;
            }

            scored.add(new ScoredChunk(chunk, similarity));
        }

        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        logger.info("Retrieval: {} candidates from policy docs, returning top {} (best score: {})",
                scored.size(), Math.min(TOP_K, scored.size()),
                scored.isEmpty() ? "N/A" : String.format("%.4f", scored.getFirst().score));

        return scored.stream().limit(TOP_K).toList();
    }

    private Set<UUID> buildPolicyVersionIds(UUID tenantId) {
        List<Document> allDocs = documentRepo.findAll().stream()
                .filter(d -> d.getTenantId().equals(tenantId))
                .toList();

        Set<UUID> policyDocIds = allDocs.stream()
                .filter(d -> d.getType() != DocumentType.QUESTIONNAIRE)
                .map(Document::getId)
                .collect(Collectors.toSet());

        if (policyDocIds.isEmpty()) {
            return Set.of();
        }

        return documentVersionRepo.findAll().stream()
                .filter(dv -> policyDocIds.contains(dv.getDocumentId()))
                .map(DocumentVersion::getId)
                .collect(Collectors.toSet());
    }

    private float[] parseEmbedding(String embeddingText) {
        if (embeddingText == null || embeddingText.isBlank()) return new float[0];
        try {
            List<Double> values = objectMapper.readValue(embeddingText, new TypeReference<>() {});
            float[] result = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            logger.warn("Failed to parse embedding: {}", e.getMessage());
            return new float[0];
        }
    }

    private boolean isZeroVector(float[] vec) {
        for (float v : vec) {
            if (v != 0.0f) return false;
        }
        return true;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dotProduct / denom;
    }
}
