package com.Myself.demo.service;

import com.Myself.demo.entity.HealthReminder;
import com.Myself.demo.entity.ReminderStatus;
import com.Myself.demo.repository.HealthReminderRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Persistent daily reminders with atomic claiming and bounded retries. */
@Service
public class ReminderConfigService {

    static final int MAX_ATTEMPTS = 3;
    private static final int CLAIM_BATCH_SIZE = 100;
    private static final Duration CLAIM_TTL = Duration.ofMinutes(2);
    private static final Duration BASE_RETRY_DELAY = Duration.ofMinutes(5);

    private final HealthReminderRepository repository;

    public ReminderConfigService(HealthReminderRepository repository) {
        this.repository = repository;
    }

    public HealthReminder add(String userId, String toolName, String params, int hour, int minute) {
        validateUser(userId);
        validateTime(hour, minute);
        HealthReminder reminder = new HealthReminder();
        reminder.setUserId(userId);
        reminder.setToolName(toolName == null || toolName.isBlank() ? null : toolName.trim());
        reminder.setParams(params == null ? "" : params);
        reminder.setHour(hour);
        reminder.setMinute(minute);
        reminder.setEnabled(true);
        reminder.setStatus(ReminderStatus.ACTIVE);
        reminder.setAttemptCount(0);
        return repository.insert(reminder);
    }

    public List<HealthReminder> list(String userId) {
        validateUser(userId);
        return repository.findActiveByUserId(userId);
    }

    public HealthReminder cancel(String userId, int id) {
        validateUser(userId);
        return repository.findActiveByUserIdAndId(userId, id)
                .filter(existing -> repository.cancel(userId, id, LocalDateTime.now()))
                .orElse(null);
    }

    /** Atomically claims due rows so concurrent instances cannot both send them. */
    public List<HealthReminder> claimDueReminders(LocalDateTime now) {
        List<HealthReminder> claimed = new ArrayList<>();
        List<HealthReminder> candidates = repository.findDueCandidates(
                now, now.toLocalDate(), now.getHour(), now.getMinute(), CLAIM_BATCH_SIZE);
        for (HealthReminder candidate : candidates) {
            boolean acquired = repository.claim(
                    candidate.getId(),
                    now,
                    now.toLocalDate(),
                    now.getHour(),
                    now.getMinute(),
                    now.plus(CLAIM_TTL));
            if (acquired) {
                candidate.setStatus(ReminderStatus.RUNNING);
                candidate.setLockedUntil(now.plus(CLAIM_TTL));
                claimed.add(candidate);
            }
        }
        return claimed;
    }

    public void markSucceeded(HealthReminder reminder, LocalDateTime now) {
        repository.markSucceeded(reminder.getId(), now.toLocalDate(), now);
        reminder.setStatus(ReminderStatus.ACTIVE);
        reminder.setLastTriggeredDate(now.toLocalDate());
        reminder.setAttemptCount(0);
        reminder.setNextRetryAt(null);
        reminder.setLockedUntil(null);
        reminder.setLastError(null);
    }

    public void markFailed(HealthReminder reminder, Throwable error, LocalDateTime now) {
        int currentAttempts = reminder.getAttemptCount() == null ? 0 : reminder.getAttemptCount();
        int attempt = Math.max(0, currentAttempts) + 1;
        boolean terminal = attempt >= MAX_ATTEMPTS;
        LocalDateTime nextRetryAt = terminal ? null : now.plus(retryDelay(attempt));
        String errorType = error == null ? "UNKNOWN" : error.getClass().getSimpleName();
        repository.markFailed(
                reminder.getId(), attempt, nextRetryAt, errorType, terminal, now);
        reminder.setAttemptCount(attempt);
        reminder.setStatus(terminal ? ReminderStatus.DEAD : ReminderStatus.FAILED);
        reminder.setEnabled(!terminal);
        reminder.setNextRetryAt(nextRetryAt);
        reminder.setLockedUntil(null);
        reminder.setLastError(errorType);
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.max(0, Math.min(attempt - 1, 3));
        return BASE_RETRY_DELAY.multipliedBy(multiplier);
    }

    private void validateUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
    }

    private void validateTime(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("提醒时间无效");
        }
    }
}
