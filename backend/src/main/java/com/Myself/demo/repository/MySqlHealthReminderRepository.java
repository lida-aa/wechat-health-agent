package com.Myself.demo.repository;

import com.Myself.demo.entity.HealthReminder;
import com.Myself.demo.mapper.HealthReminderMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MySqlHealthReminderRepository implements HealthReminderRepository {

    private final HealthReminderMapper mapper;

    public MySqlHealthReminderRepository(HealthReminderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public HealthReminder insert(HealthReminder reminder) {
        mapper.insert(reminder);
        return reminder;
    }

    @Override
    public List<HealthReminder> findActiveByUserId(String userId) {
        return mapper.findActiveByUserId(userId);
    }

    @Override
    public Optional<HealthReminder> findActiveByUserIdAndId(String userId, int id) {
        return Optional.ofNullable(mapper.findActiveByUserIdAndId(userId, id));
    }

    @Override
    public boolean cancel(String userId, int id, LocalDateTime updatedAt) {
        return mapper.cancel(userId, id, updatedAt) == 1;
    }

    @Override
    public List<HealthReminder> findDueCandidates(
            LocalDateTime now, LocalDate today, int hour, int minute, int limit) {
        return mapper.findDueCandidates(now, today, hour, minute, limit);
    }

    @Override
    public boolean claim(int id, LocalDateTime now, LocalDate today,
                         int hour, int minute, LocalDateTime lockedUntil) {
        return mapper.claim(id, now, today, hour, minute, lockedUntil) == 1;
    }

    @Override
    public void markSucceeded(int id, LocalDate triggeredDate, LocalDateTime updatedAt) {
        mapper.markSucceeded(id, triggeredDate, updatedAt);
    }

    @Override
    public void markFailed(int id, int attemptCount, LocalDateTime nextRetryAt,
                           String lastError, boolean terminal, LocalDateTime updatedAt) {
        mapper.markFailed(id, attemptCount, nextRetryAt, lastError, terminal, updatedAt);
    }
}
