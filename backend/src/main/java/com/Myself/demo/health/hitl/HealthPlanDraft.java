package com.Myself.demo.health.hitl;

public record HealthPlanDraft(
        String conditions,
        String goal,
        String content) implements HealthActionPayload {
}
