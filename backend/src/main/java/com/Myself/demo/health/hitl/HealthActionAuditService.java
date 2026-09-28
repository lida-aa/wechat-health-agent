package com.Myself.demo.health.hitl;

import com.Myself.demo.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Writes privacy-minimized lifecycle events for health actions.
 *
 * <p>Audit details intentionally exclude the action payload. Health values,
 * food descriptions and medication names must stay in the health record, not
 * in operational logs.</p>
 */
@Slf4j
@Service
public class HealthActionAuditService {

    private static final String RESOURCE_TYPE = "HEALTH_ACTION";

    private final AuditLogService auditLog;

    public HealthActionAuditService(AuditLogService auditLog) {
        this.auditLog = auditLog;
    }

    public void draftCreated(PendingHealthAction action) {
        writeSuccess(action, "HEALTH_DRAFT_CREATED", metadata(action));
    }

    public void draftUpdated(PendingHealthAction action) {
        writeSuccess(action, "HEALTH_DRAFT_UPDATED", metadata(action));
    }

    public void executionStarted(PendingHealthAction action) {
        writeSuccess(action, "HEALTH_ACTION_CONFIRMED", metadata(action));
    }

    public void completed(PendingHealthAction action, long durationMs) {
        writeSuccess(action, "HEALTH_ACTION_COMPLETED",
                metadata(action) + ",durationMs=" + nonNegative(durationMs));
    }

    public void cancelled(PendingHealthAction action) {
        writeSuccess(action, "HEALTH_ACTION_CANCELLED", metadata(action));
    }

    public void expired(PendingHealthAction action) {
        writeFail(action, "HEALTH_ACTION_EXPIRED", metadata(action));
    }

    public void duplicateIgnored(PendingHealthAction action) {
        writeFail(action, "HEALTH_ACTION_DUPLICATE_IGNORED", metadata(action));
    }

    public void failed(PendingHealthAction action, long durationMs, Throwable error) {
        String errorType = error == null ? "Unknown" : error.getClass().getSimpleName();
        writeFail(action, "HEALTH_ACTION_FAILED",
                metadata(action)
                        + ",durationMs=" + nonNegative(durationMs)
                        + ",errorType=" + errorType);
    }

    private String metadata(PendingHealthAction action) {
        return "type=" + action.type() + ",status=" + action.status();
    }

    private long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private void writeSuccess(PendingHealthAction action, String event, String detail) {
        write(action, event, detail, true);
    }

    private void writeFail(PendingHealthAction action, String event, String detail) {
        write(action, event, detail, false);
    }

    private void write(PendingHealthAction action, String event, String detail, boolean success) {
        try {
            auditLog.log(
                    action.userId(),
                    event,
                    RESOURCE_TYPE,
                    action.actionId(),
                    detail,
                    null,
                    success);
        } catch (Exception e) {
            // Auditing is best-effort here: a database outage must not turn a
            // successfully persisted health action into a retryable failure.
            log.warn("健康动作审计写入失败: event={}, actionId={}, errorType={}",
                    event, action.actionId(), e.getClass().getSimpleName());
        }
    }
}
