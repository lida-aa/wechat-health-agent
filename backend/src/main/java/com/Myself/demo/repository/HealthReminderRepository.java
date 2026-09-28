package com.Myself.demo.repository;

import com.Myself.demo.entity.HealthReminder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HealthReminderRepository {

    HealthReminder insert(HealthReminder reminder);

    List<HealthReminder> findActiveByUserId(String userId);

    Optional<HealthReminder> findActiveByUserIdAndId(String userId, int id);

    boolean cancel(String userId, int id, LocalDateTime updatedAt);

    List<HealthReminder> findDueCandidates(
            LocalDateTime now, LocalDate today, int hour, int minute, int limit);

    boolean claim(int id, LocalDateTime now, LocalDate today,
                  int hour, int minute, LocalDateTime lockedUntil);

    void markSucceeded(int id, LocalDate triggeredDate, LocalDateTime updatedAt);

    void markFailed(int id, int attemptCount, LocalDateTime nextRetryAt,
                    String lastError, boolean terminal, LocalDateTime updatedAt);
}
