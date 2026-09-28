package com.Myself.demo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RagService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagService(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService, DocumentService documentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.documentService = documentService;
    }

    @Transactional
    public void ingestDocument(byte[] bytes, String fileName, String uploader) {
        try {
            String text = documentService.extractText(bytes, fileName);
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("文档内容为空或格式不受支持");
            }
            List<String> chunks = documentService.splitText(text, 1500, 150);
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("文档没有可入库的文本");
            }

            List<String> embeddings = new ArrayList<>(chunks.size());
            for (String chunk : chunks) {
                List<Float> vector = embeddingService.embed(chunk);
                embeddings.add(objectMapper.writeValueAsString(vector));
            }

            jdbcTemplate.update("DELETE FROM doc_embeddings WHERE doc_name = ?", fileName);
            for (int i = 0; i < chunks.size(); i++) {
                jdbcTemplate.update(
                        "INSERT INTO doc_embeddings (doc_name, chunk_index, chunk_text, embedding, uploader) VALUES (?, ?, ?, ?, ?)",
                        fileName, i, chunks.get(i), embeddings.get(i), uploader);
            }
            log.info("健康知识文档入库完成: chunks={}, textChars={}", chunks.size(), text.length());
        } catch (Exception e) {
            log.error("健康知识文档入库失败: errorType={}", e.getClass().getSimpleName());
            throw new IllegalStateException("健康知识文档入库失败", e);
        }
    }

    public List<Map<String, Object>> listDocuments(String uploader) {
        if (uploader == null || uploader.isBlank()) {
            return jdbcTemplate.queryForList(
                    "SELECT doc_name, COUNT(*) AS chunks, SUM(LENGTH(chunk_text)) AS total_chars "
                            + "FROM doc_embeddings GROUP BY doc_name ORDER BY doc_name");
        }
        return jdbcTemplate.queryForList(
                "SELECT doc_name, COUNT(*) AS chunks, SUM(LENGTH(chunk_text)) AS total_chars "
                        + "FROM doc_embeddings WHERE uploader = ? GROUP BY doc_name ORDER BY doc_name",
                uploader.trim());
    }

    public List<Map<String, Object>> getDocumentChunks(String docName) {
        return jdbcTemplate.queryForList(
            "SELECT chunk_index, chunk_text FROM doc_embeddings WHERE doc_name = ? ORDER BY chunk_index", docName);
    }

    public List<Map<String, Object>> searchDocuments(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) return List.of();
        try {

            List<Float> queryVec = embeddingService.embed(kw);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT doc_name, chunk_index, chunk_text, uploader, embedding FROM doc_embeddings");
            List<Map.Entry<Map<String, Object>, Double>> scored = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String embJson = (String) row.get("embedding");
                if (embJson == null || embJson.isEmpty()) continue;
                List<Float> docVec = objectMapper.readValue(embJson, new TypeReference<List<Float>>() {});
                double score = embeddingService.cosineSimilarity(queryVec, docVec);
                row.remove("embedding");
                scored.add(Map.entry(row, score));
            }
            scored.removeIf(e -> e.getValue() < 0.45);
            if (scored.isEmpty()) return List.of();

            scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            double cut = scored.get(0).getValue() * 0.6;
            scored.removeIf(e -> e.getValue() < cut);

            int limit = Math.min(10, scored.size());
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < limit; i++) result.add(scored.get(i).getKey());
            return result;
        } catch (Exception e) {
            log.warn("语义检索失败, 回退 LIKE:{}:",e.getMessage());
            String like = "%" + kw + "%";
            return jdbcTemplate.queryForList(
                    "SELECT doc_name, chunk_index, chunk_text, uploader, uploaded_at FROM doc_embeddings "
                            + "WHERE doc_name LIKE ? OR chunk_text LIKE ? "
                            + "ORDER BY uploaded_at DESC LIMIT 100", like, like);
        }
    }
    public int deleteDocument(String docName, String uploader) {
        if (uploader == null || uploader.isBlank()) {
            return jdbcTemplate.update("DELETE FROM doc_embeddings WHERE doc_name = ?", docName);
        }
        return jdbcTemplate.update(
                "DELETE FROM doc_embeddings WHERE doc_name = ? AND uploader = ?", docName, uploader.trim());
    }

    public int countDocuments() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT doc_name) FROM doc_embeddings", Long.class);
        return c == null ? 0 : c.intValue();
    }
}
