-- Canonical schema for the focused WeChat health agent.
-- This file contains active tables only. Existing legacy media tables are not
-- dropped automatically so upgrades remain recoverable.

CREATE TABLE IF NOT EXISTS chat_history (
    user_id VARCHAR(128) NOT NULL PRIMARY KEY,
    content LONGTEXT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_traces (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    round_num INT NOT NULL DEFAULT 0,
    tool_name VARCHAR(128) NOT NULL,
    tool_args TEXT,
    tool_result TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'done',
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_trace_user_time (user_id, created_at),
    INDEX idx_agent_trace_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_user_info (
    from_user_id VARCHAR(128) NOT NULL PRIMARY KEY,
    nickname VARCHAR(128),
    first_msg_time DATETIME,
    last_msg_time DATETIME,
    msg_count INT NOT NULL DEFAULT 0,
    INDEX idx_admin_user_last_message (last_msg_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_user_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_user_id VARCHAR(128) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin_message_user_time (from_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS doc_embeddings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_name VARCHAR(256) NOT NULL,
    chunk_index INT NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding JSON NOT NULL,
    uploader VARCHAR(128) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FULLTEXT INDEX ft_health_knowledge_text (chunk_text) WITH PARSER ngram,
    INDEX idx_health_knowledge_owner (uploader),
    INDEX idx_health_knowledge_document (doc_name, chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS health_record (
    user_id VARCHAR(128) NOT NULL PRIMARY KEY,
    document_json JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_health_record_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS health_reminder (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    tool_name VARCHAR(64) DEFAULT NULL,
    params TEXT NOT NULL,
    reminder_hour TINYINT UNSIGNED NOT NULL,
    reminder_minute TINYINT UNSIGNED NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_triggered_date DATE DEFAULT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME DEFAULT NULL,
    locked_until DATETIME DEFAULT NULL,
    last_error VARCHAR(128) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_reminder_user (user_id, enabled),
    INDEX idx_reminder_due (enabled, status, reminder_hour, reminder_minute),
    INDEX idx_reminder_retry (enabled, status, next_retry_at),
    INDEX idx_reminder_lock (enabled, status, locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64),
    detail VARCHAR(512),
    ip_address VARCHAR(64),
    result VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user_time (user_id, created_at),
    INDEX idx_audit_action_time (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
