package com.Myself.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest {

    @Test
    void storesDocumentOwnershipInSchemaUploaderColumn() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        DocumentService documentService = mock(DocumentService.class);
        RagService service = new RagService(jdbcTemplate, embeddingService, documentService);
        byte[] document = "health content".getBytes();

        when(documentService.extractText(document, "guide.pdf")).thenReturn("health content");
        when(documentService.splitText("health content", 1500, 150)).thenReturn(List.of("health content"));
        when(embeddingService.embed("health content")).thenReturn(List.of(0.1f, 0.2f));

        service.ingestDocument(document, "guide.pdf", "admin");

        verify(jdbcTemplate).update("DELETE FROM doc_embeddings WHERE doc_name = ?", "guide.pdf");
        verify(jdbcTemplate).update(
                eq("INSERT INTO doc_embeddings (doc_name, chunk_index, chunk_text, embedding, uploader) VALUES (?, ?, ?, ?, ?)"),
                eq("guide.pdf"), eq(0), eq("health content"), any(String.class), eq("admin"));
    }

    @Test
    void scopedDeleteCannotRemoveAnotherUploadersDocument() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RagService service = new RagService(
                jdbcTemplate, mock(EmbeddingService.class), mock(DocumentService.class));

        service.deleteDocument("guide.pdf", "admin-a");

        verify(jdbcTemplate).update(
                "DELETE FROM doc_embeddings WHERE doc_name = ? AND uploader = ?",
                "guide.pdf", "admin-a");
    }
}
