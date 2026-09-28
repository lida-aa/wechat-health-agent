package com.Myself.demo.mapper;

import com.Myself.demo.entity.HealthReminder;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface HealthReminderMapper {

    int insert(HealthReminder reminder);

    List<HealthReminder> findActiveByUserId(@Param("userId") String userId);

    HealthReminder findActiveByUserIdAndId(
            @Param("userId") String userId, @Param("id") int id);

    int cancel(@Param("userId") String userId, @Param("id") int id,
               @Param("updatedAt") LocalDateTime updatedAt);

    List<HealthReminder> findDueCandidates(
            @Param("now") LocalDateTime now,
            @Param("today") LocalDate today,
            @Param("hour") int hour,
            @Param("minute") int minute,
            @Param("limit") int limit);

    int claim(
            @Param("id") int id,
            @Param("now") LocalDateTime now,
            @Param("today") LocalDate today,
            @Param("hour") int hour,
            @Param("minute") int minute,
            @Param("lockedUntil") LocalDateTime lockedUntil);

    int markSucceeded(
            @Param("id") int id,
            @Param("triggeredDate") LocalDate triggeredDate,
            @Param("updatedAt") LocalDateTime updatedAt);

    int markFailed(
            @Param("id") int id,
            @Param("attemptCount") int attemptCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("terminal") boolean terminal,
            @Param("updatedAt") LocalDateTime updatedAt);
}
