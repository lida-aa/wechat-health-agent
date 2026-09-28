package com.Myself.demo.health.hitl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class PendingHealthActionStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    private static final String REDIS_PREFIX = "health:pending-action:";

    private final ConcurrentHashMap<String, PendingHealthAction> actionsByUser = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private StringRedisTemplate redis;

    public PendingHealthActionStore() {
        this(Clock.systemUTC(), DEFAULT_TTL, null);
    }

    PendingHealthActionStore(Clock clock, Duration ttl) {
        this(clock, ttl, null);
    }

    PendingHealthActionStore(Clock clock, Duration ttl, StringRedisTemplate redis) {
        this.clock = clock;
        this.ttl = ttl;
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
    }

    public PendingHealthAction createMealDraft(String userId, String foods, int estimatedCalories,
                                               String mealType, String note) {
        validateUser(userId);
        if (foods == null || foods.isBlank()) {
            throw new IllegalArgumentException("饮食内容不能为空");
        }
        MealDraft draft = new MealDraft(
                foods.trim(),
                Math.max(0, estimatedCalories),
                normalizeMealType(mealType),
                note == null ? "" : note.trim());
        return saveNew(userId, PendingHealthAction.Type.RECORD_MEAL, draft);
    }

    public PendingHealthAction createVitalDraft(String userId, String type, String value, String measuredAt) {
        validateUser(userId);
        if (type == null || type.isBlank() || value == null || value.isBlank()) {
            throw new IllegalArgumentException("体征类型和值不能为空");
        }
        VitalDraft draft = new VitalDraft(
                type.trim(),
                value.trim(),
                measuredAt == null ? "" : measuredAt.trim());
        return saveNew(userId, PendingHealthAction.Type.RECORD_VITAL, draft);
    }

    public PendingHealthAction createProfileDraft(String userId, String key, String value) {
        validateUser(userId);
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new IllegalArgumentException("档案字段和值不能为空");
        }
        return saveNew(userId, PendingHealthAction.Type.UPDATE_PROFILE,
                new ProfileDraft(key.trim(), value.trim()));
    }

    public PendingHealthAction createMedicationDraft(String userId, String name, String dosage,
                                                      String schedule, int remaining) {
        validateUser(userId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("药品名称不能为空");
        }
        return saveNew(userId, PendingHealthAction.Type.ADD_MEDICATION,
                new MedicationDraft(
                        name.trim(),
                        dosage == null ? "" : dosage.trim(),
                        schedule == null ? "" : schedule.trim(),
                        Math.max(0, remaining)));
    }

    public PendingHealthAction createMedicationLogDraft(String userId, String name, int quantity,
                                                         String takenAt) {
        validateUser(userId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("药品名称不能为空");
        }
        return saveNew(userId, PendingHealthAction.Type.LOG_MEDICATION,
                new MedicationLogDraft(
                        name.trim(),
                        Math.max(1, quantity),
                        takenAt == null ? "" : takenAt.trim()));
    }

    public PendingHealthAction createHealthReportDraft(String userId, String reportText) {
        validateUser(userId);
        if (reportText == null || reportText.isBlank()) {
            throw new IllegalArgumentException("体检报告内容不能为空");
        }
        return saveNew(userId, PendingHealthAction.Type.SAVE_HEALTH_REPORT,
                new HealthReportDraft(reportText.trim()));
    }

    public PendingHealthAction createHealthPlanDraft(String userId, String conditions,
                                                      String goal, String content) {
        validateUser(userId);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("健康计划内容不能为空");
        }
        return saveNew(userId, PendingHealthAction.Type.CREATE_HEALTH_PLAN,
                new HealthPlanDraft(
                        conditions == null ? "" : conditions.trim(),
                        goal == null ? "" : goal.trim(),
                        content.trim()));
    }

    public PendingHealthAction createReminderDraft(String userId, int hour, int minute,
                                                    String toolName, String params, String label) {
        validateUser(userId);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("提醒时间无效");
        }
        return saveNew(userId, PendingHealthAction.Type.CREATE_REMINDER,
                new ReminderDraft(
                        hour,
                        minute,
                        toolName == null || toolName.isBlank() ? null : toolName.trim(),
                        params == null ? "" : params.trim(),
                        label == null ? "" : label.trim()));
    }

    public PendingHealthAction createReminderCancellationDraft(
            String userId, int reminderId, int hour, int minute, String label) {
        validateUser(userId);
        if (reminderId <= 0) {
            throw new IllegalArgumentException("提醒编号无效");
        }
        return saveNew(userId, PendingHealthAction.Type.CANCEL_REMINDER,
                new ReminderCancellationDraft(
                        reminderId,
                        hour,
                        minute,
                        label == null ? "" : label.trim()));
    }

    public Optional<PendingHealthAction> currentForUser(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        loadFromRedisIfNecessary(userId);

        AtomicReference<PendingHealthAction> result = new AtomicReference<>();
        actionsByUser.computeIfPresent(userId, (key, current) -> {
            PendingHealthAction checked = expireIfNecessary(current);
            result.set(checked);
            return checked;
        });
        persist(result.get());
        return Optional.ofNullable(result.get());
    }

    public Optional<PendingHealthAction> updateMealType(String userId, String actionId, String mealType) {
        return update(userId, actionId, current -> {
            if (current.status() != PendingHealthAction.Status.WAITING_CONFIRMATION
                    || current.type() != PendingHealthAction.Type.RECORD_MEAL) {
                return current;
            }
            MealDraft old = current.payloadAs(MealDraft.class);
            return current.withPayload(new MealDraft(
                    old.foods(), old.estimatedCalories(), normalizeMealType(mealType), old.note()));
        });
    }

    public Optional<PendingHealthAction> claimForExecution(String userId, String actionId) {
        return transition(userId, actionId,
                PendingHealthAction.Status.WAITING_CONFIRMATION,
                PendingHealthAction.Status.EXECUTING);
    }

    public Optional<PendingHealthAction> markCompleted(String userId, String actionId) {
        return transition(userId, actionId,
                PendingHealthAction.Status.EXECUTING,
                PendingHealthAction.Status.COMPLETED);
    }

    public Optional<PendingHealthAction> markFailed(String userId, String actionId) {
        return transition(userId, actionId,
                PendingHealthAction.Status.EXECUTING,
                PendingHealthAction.Status.FAILED);
    }

    public Optional<PendingHealthAction> cancel(String userId, String actionId) {
        return transition(userId, actionId,
                PendingHealthAction.Status.WAITING_CONFIRMATION,
                PendingHealthAction.Status.CANCELLED);
    }

    private PendingHealthAction saveNew(String userId, PendingHealthAction.Type type,
                                        HealthActionPayload payload) {
        Instant now = clock.instant();
        PendingHealthAction action = new PendingHealthAction(
                UUID.randomUUID().toString(),
                userId,
                type,
                payload,
                PendingHealthAction.Status.WAITING_CONFIRMATION,
                now,
                now.plus(ttl));
        actionsByUser.put(userId, action);
        persist(action);
        return action;
    }

    private Optional<PendingHealthAction> transition(String userId, String actionId,
                                                     PendingHealthAction.Status expected,
                                                     PendingHealthAction.Status next) {
        loadFromRedisIfNecessary(userId);
        AtomicReference<PendingHealthAction> changed = new AtomicReference<>();
        actionsByUser.computeIfPresent(userId, (key, current) -> {
            PendingHealthAction checked = expireIfNecessary(current);
            if (!checked.actionId().equals(actionId) || checked.status() != expected) return checked;
            PendingHealthAction updated = checked.withStatus(next);
            changed.set(updated);
            return updated;
        });
        persist(changed.get());
        return Optional.ofNullable(changed.get());
    }

    private Optional<PendingHealthAction> update(String userId, String actionId,
                                                 java.util.function.UnaryOperator<PendingHealthAction> updater) {
        loadFromRedisIfNecessary(userId);
        AtomicReference<PendingHealthAction> changed = new AtomicReference<>();
        actionsByUser.computeIfPresent(userId, (key, current) -> {
            PendingHealthAction checked = expireIfNecessary(current);
            if (!checked.actionId().equals(actionId)) return checked;
            PendingHealthAction updated = updater.apply(checked);
            if (updated != checked) changed.set(updated);
            return updated;
        });
        persist(changed.get());
        return Optional.ofNullable(changed.get());
    }

    private PendingHealthAction expireIfNecessary(PendingHealthAction action) {
        if (action.status() == PendingHealthAction.Status.WAITING_CONFIRMATION
                && !clock.instant().isBefore(action.expiresAt())) {
            return action.withStatus(PendingHealthAction.Status.EXPIRED);
        }
        return action;
    }

    private void loadFromRedisIfNecessary(String userId) {
        if (actionsByUser.containsKey(userId) || redis == null) return;
        try {
            String json = redis.opsForValue().get(redisKey(userId));
            if (json == null || json.isBlank()) return;
            PendingHealthAction action = objectMapper.readValue(json, StoredAction.class).toAction();
            if (!userId.equals(action.userId())) {
                log.warn("忽略用户不匹配的健康草稿: requested={}, stored={}", userId, action.userId());
                return;
            }
            actionsByUser.putIfAbsent(userId, action);
        } catch (Exception e) {
            log.warn("Redis 健康草稿读取失败，使用内存状态: {}", e.getMessage());
        }
    }

    private void persist(PendingHealthAction action) {
        if (action == null || redis == null) return;
        try {
            String json = objectMapper.writeValueAsString(StoredAction.from(action));
            Duration storageTtl = Duration.between(clock.instant(), action.expiresAt().plus(ttl));
            if (storageTtl.isNegative() || storageTtl.isZero()) storageTtl = Duration.ofMinutes(1);
            redis.opsForValue().set(redisKey(action.userId()), json, storageTtl);
        } catch (Exception e) {
            log.warn("Redis 健康草稿写入失败，已保留内存状态: {}", e.getMessage());
        }
    }

    private String redisKey(String userId) {
        return REDIS_PREFIX + userId;
    }

    private void validateUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
    }

    private String normalizeMealType(String mealType) {
        if (mealType == null) return "未指定";
        String value = mealType.trim();
        if (value.contains("早餐") || value.contains("早饭")) return "早餐";
        if (value.contains("午餐") || value.contains("午饭")) return "午餐";
        if (value.contains("晚餐") || value.contains("晚饭")) return "晚餐";
        if (value.contains("加餐") || value.contains("夜宵")) return "加餐";
        return value.isBlank() ? "未指定" : value;
    }

    private record StoredAction(
            String actionId,
            String userId,
            PendingHealthAction.Type type,
            HealthActionPayload payload,
            PendingHealthAction.Status status,
            long createdAtEpochMilli,
            long expiresAtEpochMilli) {

        static StoredAction from(PendingHealthAction action) {
            return new StoredAction(
                    action.actionId(),
                    action.userId(),
                    action.type(),
                    action.payload(),
                    action.status(),
                    action.createdAt().toEpochMilli(),
                    action.expiresAt().toEpochMilli());
        }

        PendingHealthAction toAction() {
            return new PendingHealthAction(
                    actionId,
                    userId,
                    type,
                    payload,
                    status,
                    Instant.ofEpochMilli(createdAtEpochMilli),
                    Instant.ofEpochMilli(expiresAtEpochMilli));
        }
    }
}
