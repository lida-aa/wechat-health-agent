package com.Myself.demo.health.hitl;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Typed data carried by a pending health action.
 *
 * <p>The discriminator is persisted with the payload so Redis recovery does
 * not require nullable fields for every supported action type.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MealDraft.class, name = "meal"),
        @JsonSubTypes.Type(value = VitalDraft.class, name = "vital"),
        @JsonSubTypes.Type(value = ProfileDraft.class, name = "profile"),
        @JsonSubTypes.Type(value = MedicationDraft.class, name = "medication"),
        @JsonSubTypes.Type(value = MedicationLogDraft.class, name = "medication_log"),
        @JsonSubTypes.Type(value = HealthReportDraft.class, name = "health_report"),
        @JsonSubTypes.Type(value = HealthPlanDraft.class, name = "health_plan"),
        @JsonSubTypes.Type(value = ReminderDraft.class, name = "reminder"),
        @JsonSubTypes.Type(value = ReminderCancellationDraft.class, name = "reminder_cancellation")
})
public sealed interface HealthActionPayload permits
        MealDraft, VitalDraft, ProfileDraft, MedicationDraft, MedicationLogDraft,
        HealthReportDraft, HealthPlanDraft, ReminderDraft, ReminderCancellationDraft {
}
