package com.Myself.demo.health.hitl;

public record VitalDraft(
        String type,
        String value,
        String measuredAt) implements HealthActionPayload {
}
