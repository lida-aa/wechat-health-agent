package com.Myself.demo.health.hitl;

public record ReminderCancellationDraft(
        int reminderId,
        int hour,
        int minute,
        String label) implements HealthActionPayload {
}
