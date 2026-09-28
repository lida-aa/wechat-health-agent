package com.Myself.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** Persisted health-record aggregate for one channel user. */
@Data
public class HealthRecordDocument {
    private String userId;
    private String documentJson;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
