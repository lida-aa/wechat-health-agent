package com.Myself.demo.bot;

import com.Myself.demo.tool.HealthManagementTool;
import com.Myself.demo.tool.ReminderTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class HealthToolPolicyTest {

    private final HealthToolPolicy policy = new HealthToolPolicy();

    @Test
    void exposesHealthWorkflowTools() {
        assertThat(policy.allowedTools())
                .contains("health_overview", "record_vital", "analyze_meal", "schedule_health_briefing");
        assertThat(policy.riskOf("health_overview")).contains(ToolRiskLevel.READ_ONLY);
        assertThat(policy.riskOf("record_vital")).contains(ToolRiskLevel.CONFIRMATION_REQUIRED);
        assertThat(policy.canRunAutomatically("daily_health_briefing")).isTrue();
        assertThat(policy.canRunAutomatically("add_medication")).isFalse();
    }

    @Test
    void rejectsLegacyAndExternalTools() {
        assertThat(policy.isAllowed("generate_travel_plan")).isFalse();
        assertThat(policy.isAllowed("tarot")).isFalse();
        assertThat(policy.isAllowed("get-tickets")).isFalse();
        assertThat(policy.isAllowed("rag_search")).isFalse();
        assertThat(policy.isAllowed("record_meal")).isFalse();
    }

    @Test
    void everySensitiveToolMethodIsDeclaredAsDraftOnly() {
        Stream.of(HealthManagementTool.class, ReminderTool.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .filter(method -> policy.requiresConfirmation(toolName(method)))
                .forEach(method -> assertThat(method.isAnnotationPresent(CreatesHealthActionDraft.class))
                        .as("sensitive tool %s must create a HITL draft", toolName(method))
                        .isTrue());
    }

    @Test
    void everyToolUserIdIsInjectedFromCurrentChannelUser() {
        Stream.of(HealthManagementTool.class, ReminderTool.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .filter(this::looksLikeUserId)
                .forEach(parameter -> assertThat(parameter.isAnnotationPresent(CurrentUserId.class))
                        .as("tool user parameter %s must use @CurrentUserId", parameter)
                        .isTrue());
    }

    private boolean looksLikeUserId(Parameter parameter) {
        P annotation = parameter.getAnnotation(P.class);
        return "userId".equalsIgnoreCase(parameter.getName())
                || (annotation != null && annotation.value().contains("用户ID"));
    }

    private String toolName(Method method) {
        String name = method.getAnnotation(Tool.class).name();
        return name == null || name.isBlank() ? method.getName() : name;
    }
}
