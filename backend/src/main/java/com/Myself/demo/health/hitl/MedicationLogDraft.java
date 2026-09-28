package com.Myself.demo.health.hitl;

public record MedicationLogDraft(
        String name,
        int quantity,
        String takenAt) implements HealthActionPayload {
}
