CREATE TABLE IF NOT EXISTS health_record (
    user_id VARCHAR(128) NOT NULL PRIMARY KEY,
    document_json JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_health_record_updated_at (updated_at)
);

-- health_vital_record is intentionally retained as a legacy table.
-- V3 makes health_record the only persistence target for the health workflow.
