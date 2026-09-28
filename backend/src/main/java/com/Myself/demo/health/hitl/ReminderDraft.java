package com.Myself.demo.health.hitl;

public record ReminderDraft(
        int hour,
        int minute,
        String toolName,
        String params,
        String label) implements HealthActionPayload {
}
