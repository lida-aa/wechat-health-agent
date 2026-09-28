package com.Myself.demo.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class HealthReminder {
    private Integer id;
    private String userId;
    private String toolName;
    private String params;
    private Integer hour;
    private Integer minute;
    private Boolean enabled;
    private ReminderStatus status;
    private LocalDate lastTriggeredDate;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lockedUntil;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
