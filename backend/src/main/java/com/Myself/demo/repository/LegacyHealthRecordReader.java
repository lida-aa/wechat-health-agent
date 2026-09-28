package com.Myself.demo.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Read-only adapter used to import records created by the former file store. */
@Component
public class LegacyHealthRecordReader {

    private final ObjectMapper objectMapper;
    private final Path dataDirectory;

    public LegacyHealthRecordReader(
            ObjectMapper objectMapper,
            @Value("${health.legacy-data-dir:health-data}") String dataDirectory) {
        this.objectMapper = objectMapper;
        this.dataDirectory = Path.of(dataDirectory);
    }

    public Optional<Map<String, Object>> read(String userId) {
        Path file = dataDirectory.resolve(safeFileName(userId));
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(
                    file.toFile(), new TypeReference<Map<String, Object>>() { }));
        } catch (IOException e) {
            throw new HealthRecordPersistenceException("旧版健康档案读取失败，已停止导入", e);
        }
    }

    private String safeFileName(String userId) {
        return userId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json";
    }
}
