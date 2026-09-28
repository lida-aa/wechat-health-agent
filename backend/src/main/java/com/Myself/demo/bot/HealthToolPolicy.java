package com.Myself.demo.bot;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Defines the only tools that the health agent may expose to the model.
 *
 * <p>This policy is intentionally explicit. Adding a Spring bean with a
 * {@code @Tool} method must not silently grant the model a new capability.</p>
 */
@Component
public class HealthToolPolicy {

    private static final Map<String, ToolRiskLevel> TOOL_RISKS = Map.ofEntries(
            readOnly("body_fat_rate"),
            readOnly("food_nutrition"),
            readOnly("health_tip"),
            readOnly("health_overview"),
            confirmationRequired("record_profile_fact"),
            confirmationRequired("add_medication"),
            confirmationRequired("record_vital"),
            confirmationRequired("log_medication"),
            readOnly("find_pharmacy"),
            readOnly("symptom_screening"),
            readOnly("interpret_health_report"),
            confirmationRequired("save_health_report"),
            readOnly("interpret_latest_health_report"),
            readOnly("open_health_exam"),
            confirmationRequired("create_health_plan"),
            readOnly("daily_health_briefing"),
            confirmationRequired("schedule_health_briefing"),
            readOnly("today_diet_summary"),
            confirmationRequired("analyze_meal"),
            confirmationRequired("create_reminder"),
            readOnly("list_reminders"),
            confirmationRequired("cancel_reminder")
    );

    public boolean isAllowed(String toolName) {
        return toolName != null && TOOL_RISKS.containsKey(toolName);
    }

    public Set<String> allowedTools() {
        return TOOL_RISKS.keySet();
    }

    public Optional<ToolRiskLevel> riskOf(String toolName) {
        return Optional.ofNullable(TOOL_RISKS.get(toolName));
    }

    public boolean requiresConfirmation(String toolName) {
        return riskOf(toolName).orElse(null) == ToolRiskLevel.CONFIRMATION_REQUIRED;
    }

    public boolean canRunAutomatically(String toolName) {
        return riskOf(toolName).orElse(null) == ToolRiskLevel.READ_ONLY;
    }

    private static Map.Entry<String, ToolRiskLevel> readOnly(String toolName) {
        return Map.entry(toolName, ToolRiskLevel.READ_ONLY);
    }

    private static Map.Entry<String, ToolRiskLevel> confirmationRequired(String toolName) {
        return Map.entry(toolName, ToolRiskLevel.CONFIRMATION_REQUIRED);
    }
}
