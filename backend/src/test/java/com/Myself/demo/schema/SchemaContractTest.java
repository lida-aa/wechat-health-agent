package com.Myself.demo.schema;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaContractTest {

    @Test
    void canonicalSchemaContainsOnlyActiveHealthAgentTables() throws Exception {
        String schema;
        try (InputStream input = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(input).isNotNull();
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(schema)
                .contains("CREATE TABLE IF NOT EXISTS health_record")
                .contains("CREATE TABLE IF NOT EXISTS health_reminder")
                .contains("CREATE TABLE IF NOT EXISTS audit_log")
                .contains("CREATE TABLE IF NOT EXISTS doc_embeddings")
                .contains("uploader VARCHAR(128)")
                .doesNotContain("CREATE TABLE IF NOT EXISTS admin_user_images")
                .doesNotContain("CREATE TABLE IF NOT EXISTS admin_user_files")
                .doesNotContain("CREATE TABLE IF NOT EXISTS health_vital_record")
                .doesNotContain("INSERT IGNORE INTO admin_user");
    }
}
