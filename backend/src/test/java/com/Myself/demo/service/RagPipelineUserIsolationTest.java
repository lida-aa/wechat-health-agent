package com.Myself.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagPipelineUserIsolationTest {

    @Test
    void relevancePrecheckOnlyLoadsGlobalAndCurrentUsersDocuments() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RagPipelineService service = new RagPipelineService(
                jdbcTemplate, mock(EmbeddingService.class), mock(LlmService.class));
        when(jdbcTemplate.queryForList(
                "SELECT id, embedding FROM doc_embeddings WHERE uploader IS NULL OR uploader = ?",
                "user-a")).thenReturn(List.of());

        service.buildContext("user-a", "血压控制建议", 3);

        verify(jdbcTemplate).queryForList(
                "SELECT id, embedding FROM doc_embeddings WHERE uploader IS NULL OR uploader = ?",
                "user-a");
    }

    @Test
    void retrievalCacheIsSeparatedByUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        RagPipelineService service = new RagPipelineService(
                jdbcTemplate, embeddingService, mock(LlmService.class));
        String query = "血压控制建议";
        when(embeddingService.embed(query)).thenReturn(List.of(1.0f));

        Map<String, Object> row = new HashMap<>();
        row.put("id", 1L);
        row.put("chunk_text", "控制盐摄入");
        row.put("embedding", "[1.0]");
        row.put("score", 1.0d);
        when(jdbcTemplate.queryForList(contains("MATCH(chunk_text)"), eq(query), eq("user-a")))
                .thenReturn(List.of(new HashMap<>(row)));
        when(jdbcTemplate.queryForList(contains("MATCH(chunk_text)"), eq(query), eq("user-b")))
                .thenReturn(List.of(new HashMap<>(row)));

        service.searchDirect("user-a", query, 3);
        service.searchDirect("user-b", query, 3);

        verify(jdbcTemplate).queryForList(contains("MATCH(chunk_text)"), eq(query), eq("user-a"));
        verify(jdbcTemplate).queryForList(contains("MATCH(chunk_text)"), eq(query), eq("user-b"));
    }
}
