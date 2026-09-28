package com.Myself.demo.health.hitl.handler;

final class HealthActionText {

    private HealthActionText() {
    }

    static String display(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String preview(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }
}
