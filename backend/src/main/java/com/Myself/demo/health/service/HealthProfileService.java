package com.Myself.demo.health.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.Myself.demo.health.service.HealthRecordValues.now;

@Service
public class HealthProfileService {

    private final HealthRecordStore store;

    public HealthProfileService(HealthRecordStore store) {
        this.store = store;
    }

    public String recordFact(String userId, String key, String value) {
        return store.update(userId, data -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) data.computeIfAbsent(
                    "profile", ignored -> new LinkedHashMap<>());
            profile.put(key, value);
            profile.put("最后更新", now());
            return "已更新健康档案：" + key + " = " + value;
        });
    }
}
