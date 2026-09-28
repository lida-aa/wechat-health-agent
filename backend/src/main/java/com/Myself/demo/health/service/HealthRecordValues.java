package com.Myself.demo.health.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class HealthRecordValues {

    static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("M月d日 HH时");

    private HealthRecordValues() {
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(Map<String, Object> record, String key) {
        Object value = record.computeIfAbsent(key, ignored -> new ArrayList<Map<String, Object>>());
        return (List<Map<String, Object>>) value;
    }

    static int intValue(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    static String safe(String value, String fallback) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? fallback : value;
    }

    static String summary(Object value, int limit) {
        String text = value == null ? "" : String.valueOf(value).replaceAll("\\s+", " ").trim();
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }

    static String now() {
        return LocalDateTime.now().format(DISPLAY_TIME);
    }
}
