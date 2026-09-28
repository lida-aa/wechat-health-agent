package com.Myself.demo.repository;

import com.Myself.demo.entity.HealthRecordDocument;
import com.Myself.demo.mapper.HealthRecordMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Repository
public class MySqlHealthRecordRepository implements HealthRecordRepository {

    private final HealthRecordMapper mapper;
    private final ObjectMapper objectMapper;
    private final LegacyHealthRecordReader legacyReader;

    public MySqlHealthRecordRepository(
            HealthRecordMapper mapper,
            ObjectMapper objectMapper,
            LegacyHealthRecordReader legacyReader) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.legacyReader = legacyReader;
    }

    @Override
    @Transactional
    public Map<String, Object> load(String userId) {
        requireUserId(userId);
        HealthRecordDocument stored = mapper.findByUserId(userId);
        if (stored != null) {
            return deserialize(stored.getDocumentJson());
        }

        Map<String, Object> legacy = legacyReader.read(userId).orElse(null);
        if (legacy == null) {
            return new LinkedHashMap<>();
        }
        save(userId, legacy);
        log.info("已将一份旧版文件健康档案导入 MySQL，原文件保留为备份");
        return legacy;
    }

    @Override
    @Transactional
    public void save(String userId, Map<String, Object> record) {
        requireUserId(userId);
        try {
            String json = objectMapper.writeValueAsString(record);
            if (mapper.updateDocument(userId, json) > 0) {
                return;
            }

            HealthRecordDocument document = new HealthRecordDocument();
            document.setUserId(userId);
            document.setDocumentJson(json);
            document.setVersion(0L);
            try {
                mapper.insert(document);
            } catch (DuplicateKeyException concurrentInsert) {
                if (mapper.updateDocument(userId, json) == 0) {
                    throw concurrentInsert;
                }
            }
        } catch (HealthRecordPersistenceException e) {
            throw e;
        } catch (Exception e) {
            throw new HealthRecordPersistenceException("健康档案保存失败", e);
        }
    }

    private Map<String, Object> deserialize(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new HealthRecordPersistenceException("数据库中的健康档案格式无效", e);
        }
    }

    private void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
    }
}
