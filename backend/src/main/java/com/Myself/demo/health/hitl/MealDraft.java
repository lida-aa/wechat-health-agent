package com.Myself.demo.health.hitl;

public record MealDraft(
        String foods,
        int estimatedCalories,
        String mealType,
        String note) implements HealthActionPayload {
}
