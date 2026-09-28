package com.Myself.demo.support;

import com.Myself.demo.repository.HealthRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryHealthRecordRepository implements HealthRecordRepository {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> records = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> load(String userId) {
        String json = records.get(userId);
        if (json == null) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void save(String userId, Map<String, Object> record) {
        try {
            records.put(userId, objectMapper.writeValueAsString(record));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
