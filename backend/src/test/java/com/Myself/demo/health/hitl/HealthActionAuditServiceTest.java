package com.Myself.demo.health.hitl;

import com.Myself.demo.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HealthActionAuditServiceTest {

    @Test
    void auditMetadataDoesNotContainHealthPayload() {
        AuditLogService auditLog = mock(AuditLogService.class);
        HealthActionAuditService audit = new HealthActionAuditService(auditLog);
        PendingHealthAction action = actionWithSensitivePayload();

        audit.draftCreated(action);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLog).log(
                eq("user-a"),
                eq("HEALTH_DRAFT_CREATED"),
                eq("HEALTH_ACTION"),
                eq("action-1"),
                detail.capture(),
                isNull(),
                eq(true));
        assertThat(detail.getValue())
                .contains("type=RECORD_MEAL", "status=WAITING_CONFIRMATION")
                .doesNotContain("降压药", "过敏", "650");
    }

    @Test
    void auditDatabaseFailureDoesNotBreakHealthAction() {
        AuditLogService auditLog = mock(AuditLogService.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(auditLog).log(
                        anyString(), anyString(), anyString(), anyString(),
                        anyString(), any(), anyBoolean());
        HealthActionAuditService audit = new HealthActionAuditService(auditLog);

        assertThatCode(() -> audit.completed(actionWithSensitivePayload(), 12))
                .doesNotThrowAnyException();
    }

    @Test
    void failureAuditRecordsOnlyExceptionType() {
        AuditLogService auditLog = mock(AuditLogService.class);
        HealthActionAuditService audit = new HealthActionAuditService(auditLog);
        PendingHealthAction action = actionWithSensitivePayload();

        audit.failed(action, 8, new IllegalArgumentException("患者的隐私错误内容"));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLog).log(
                eq("user-a"),
                eq("HEALTH_ACTION_FAILED"),
                eq("HEALTH_ACTION"),
                eq("action-1"),
                detail.capture(),
                isNull(),
                eq(false));
        assertThat(detail.getValue())
                .contains("durationMs=8", "errorType=IllegalArgumentException")
                .doesNotContain("患者的隐私错误内容");
    }

    private PendingHealthAction actionWithSensitivePayload() {
        return new PendingHealthAction(
                "action-1",
                "user-a",
                PendingHealthAction.Type.RECORD_MEAL,
                new MealDraft("降压药、过敏信息", 650, "晚餐", "敏感备注"),
                PendingHealthAction.Status.WAITING_CONFIRMATION,
                Instant.parse("2026-09-27T08:00:00Z"),
                Instant.parse("2026-09-27T08:15:00Z"));
    }
}
