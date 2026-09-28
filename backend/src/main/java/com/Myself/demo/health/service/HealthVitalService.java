package com.Myself.demo.health.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.Myself.demo.health.service.HealthRecordValues.DISPLAY_TIME;
import static com.Myself.demo.health.service.HealthRecordValues.list;

@Service
public class HealthVitalService {

    private static final Pattern FULL_DATE = Pattern.compile(
            "(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})(?:[ T]?(\\d{1,2}):(\\d{2}))?");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");

    private final HealthRecordStore store;

    public HealthVitalService(HealthRecordStore store) {
        this.store = store;
    }

    public String record(String userId, String type, String value, String measuredAt) {
        LocalDateTime parsedAt = parseDateTime(measuredAt);
        return store.update(userId, data -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type);
            item.put("value", value);
            item.put("measuredAt", parsedAt.format(DISPLAY_TIME));
            list(data, "vitals").add(item);
            return "已记录" + type + "：" + value + "。如出现持续异常或不适，请及时就医。";
        });
    }

    private LocalDateTime parseDateTime(String measuredAt) {
        if (measuredAt == null || measuredAt.isBlank()) return LocalDateTime.now();
        LocalDateTime now = LocalDateTime.now();
        try {
            String value = measuredAt.trim();
            LocalDateTime parsed = null;
            if (value.matches(".*\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*")) {
                Matcher date = FULL_DATE.matcher(value);
                if (date.find()) {
                    LocalDateTime candidate = LocalDateTime.of(
                            Integer.parseInt(date.group(1)),
                            Integer.parseInt(date.group(2)),
                            Integer.parseInt(date.group(3)),
                            date.group(4) != null ? Integer.parseInt(date.group(4)) : 0,
                            date.group(5) != null ? Integer.parseInt(date.group(5)) : 0);
                    long days = java.time.Duration.between(
                            candidate.toLocalDate().atStartOfDay(), now.toLocalDate().atStartOfDay()).toDays();
                    if (Math.abs(days) <= 1) parsed = candidate;
                }
            } else if (containsAny(value, "今天", "今早", "上午", "早晨", "早上")) {
                parsed = parseTimeOfDay(value, now);
            } else if (value.contains("昨天")) {
                parsed = parseTimeOfDay(value, now.minusDays(1));
            }
            return parsed == null ? now : parsed;
        } catch (Exception ignored) {
            return now;
        }
    }

    private LocalDateTime parseTimeOfDay(String value, LocalDateTime baseDate) {
        Matcher time = TIME.matcher(value);
        if (time.find()) {
            try {
                return baseDate.toLocalDate().atTime(
                        Integer.parseInt(time.group(1)), Integer.parseInt(time.group(2)));
            } catch (Exception ignored) {
                // Fall through to the natural-language defaults below.
            }
        }
        if (value.contains("晚")) return baseDate.toLocalDate().atTime(20, 0);
        if (value.contains("中午")) return baseDate.toLocalDate().atTime(12, 0);
        if (containsAny(value, "上午", "早晨", "早上", "今早")) {
            return baseDate.toLocalDate().atTime(8, 0);
        }
        return baseDate.toLocalDate().atStartOfDay();
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }
}
