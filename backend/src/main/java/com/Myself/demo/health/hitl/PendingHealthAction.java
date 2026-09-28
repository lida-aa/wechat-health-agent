package com.Myself.demo.health.hitl;

import java.time.Instant;

public record PendingHealthAction(
        String actionId,
        String userId,
        Type type,
        HealthActionPayload payload,
        Status status,
        Instant createdAt,
        Instant expiresAt) {

    public enum Type {
        RECORD_MEAL,
        RECORD_VITAL,
        UPDATE_PROFILE,
        ADD_MEDICATION,
        LOG_MEDICATION,
        SAVE_HEALTH_REPORT,
        CREATE_HEALTH_PLAN,
        CREATE_REMINDER,
        CANCEL_REMINDER
    }

    public enum Status {
        WAITING_CONFIRMATION,
        EXECUTING,
        COMPLETED,
        CANCELLED,
        EXPIRED,
        FAILED
    }

    public PendingHealthAction withStatus(Status nextStatus) {
        return new PendingHealthAction(actionId, userId, type, payload,
                nextStatus, createdAt, expiresAt);
    }

    public PendingHealthAction withPayload(HealthActionPayload nextPayload) {
        return new PendingHealthAction(actionId, userId, type, nextPayload,
                status, createdAt, expiresAt);
    }

    public <T extends HealthActionPayload> T payloadAs(Class<T> payloadType) {
        if (!payloadType.isInstance(payload)) {
            throw new IllegalStateException("动作 " + type + " 的数据类型错误");
        }
        return payloadType.cast(payload);
    }
}
