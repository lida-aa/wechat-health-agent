package com.Myself.demo.support;

import com.Myself.demo.entity.HealthReminder;
import com.Myself.demo.entity.ReminderStatus;
import com.Myself.demo.repository.HealthReminderRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryHealthReminderRepository implements HealthReminderRepository {

    private final Map<Integer, HealthReminder> reminders = new LinkedHashMap<>();
    private final AtomicInteger ids = new AtomicInteger(1);

    @Override
    public synchronized HealthReminder insert(HealthReminder reminder) {
        reminder.setId(ids.getAndIncrement());
        reminder.setCreatedAt(LocalDateTime.now());
        reminder.setUpdatedAt(reminder.getCreatedAt());
        reminders.put(reminder.getId(), reminder);
        return reminder;
    }

    @Override
    public synchronized List<HealthReminder> findActiveByUserId(String userId) {
        return reminders.values().stream()
                .filter(item -> userId.equals(item.getUserId()))
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> item.getStatus() != ReminderStatus.CANCELLED)
                .toList();
    }

    @Override
    public synchronized Optional<HealthReminder> findActiveByUserIdAndId(String userId, int id) {
        return Optional.ofNullable(reminders.get(id))
                .filter(item -> userId.equals(item.getUserId()))
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> item.getStatus() != ReminderStatus.CANCELLED);
    }

    @Override
    public synchronized boolean cancel(String userId, int id, LocalDateTime updatedAt) {
        Optional<HealthReminder> current = findActiveByUserIdAndId(userId, id);
        if (current.isEmpty()) return false;
        HealthReminder reminder = current.get();
        reminder.setEnabled(false);
        reminder.setStatus(ReminderStatus.CANCELLED);
        reminder.setNextRetryAt(null);
        reminder.setLockedUntil(null);
        reminder.setUpdatedAt(updatedAt);
        return true;
    }

    @Override
    public synchronized List<HealthReminder> findDueCandidates(
            LocalDateTime now, LocalDate today, int hour, int minute, int limit) {
        return reminders.values().stream()
                .filter(item -> eligible(item, now, today, hour, minute))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized boolean claim(int id, LocalDateTime now, LocalDate today,
                                      int hour, int minute, LocalDateTime lockedUntil) {
        HealthReminder reminder = reminders.get(id);
        if (!eligible(reminder, now, today, hour, minute)) return false;
        reminder.setStatus(ReminderStatus.RUNNING);
        reminder.setLockedUntil(lockedUntil);
        reminder.setUpdatedAt(now);
        return true;
    }

    @Override
    public synchronized void markSucceeded(int id, LocalDate triggeredDate, LocalDateTime updatedAt) {
        HealthReminder reminder = reminders.get(id);
        if (reminder == null || reminder.getStatus() != ReminderStatus.RUNNING) return;
        reminder.setStatus(ReminderStatus.ACTIVE);
        reminder.setLastTriggeredDate(triggeredDate);
        reminder.setAttemptCount(0);
        reminder.setNextRetryAt(null);
        reminder.setLockedUntil(null);
        reminder.setLastError(null);
        reminder.setUpdatedAt(updatedAt);
    }

    @Override
    public synchronized void markFailed(int id, int attemptCount, LocalDateTime nextRetryAt,
                                        String lastError, boolean terminal, LocalDateTime updatedAt) {
        HealthReminder reminder = reminders.get(id);
        if (reminder == null || reminder.getStatus() != ReminderStatus.RUNNING) return;
        reminder.setStatus(terminal ? ReminderStatus.DEAD : ReminderStatus.FAILED);
        reminder.setEnabled(!terminal);
        reminder.setAttemptCount(attemptCount);
        reminder.setNextRetryAt(nextRetryAt);
        reminder.setLockedUntil(null);
        reminder.setLastError(lastError);
        reminder.setUpdatedAt(updatedAt);
    }

    private boolean eligible(HealthReminder reminder, LocalDateTime now, LocalDate today,
                             int hour, int minute) {
        if (reminder == null || !Boolean.TRUE.equals(reminder.getEnabled())) return false;
        if (reminder.getLastTriggeredDate() != null
                && !reminder.getLastTriggeredDate().isBefore(today)) return false;
        return switch (reminder.getStatus()) {
            case ACTIVE -> reminder.getHour() == hour && reminder.getMinute() == minute;
            case FAILED -> reminder.getNextRetryAt() != null
                    && !reminder.getNextRetryAt().isAfter(now);
            case RUNNING -> reminder.getLockedUntil() != null
                    && !reminder.getLockedUntil().isAfter(now);
            default -> false;
        };
    }
}
