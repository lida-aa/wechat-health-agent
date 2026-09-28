package com.Myself.demo.health.hitl;

public record MedicationDraft(
        String name,
        String dosage,
        String schedule,
        int remaining) implements HealthActionPayload {
}
