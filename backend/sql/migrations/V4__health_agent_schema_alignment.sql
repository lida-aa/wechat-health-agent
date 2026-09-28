-- Non-destructive alignment for databases created by earlier demo versions.

ALTER TABLE admin_user
    ADD COLUMN IF NOT EXISTS created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE doc_embeddings
    ADD COLUMN IF NOT EXISTS uploader VARCHAR(128) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Some early local databases used user_id instead of uploader. Copy the value
-- only when that legacy column actually exists.
SET @legacy_user_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'doc_embeddings'
      AND COLUMN_NAME = 'user_id'
);
SET @copy_legacy_owner_sql = IF(
    @legacy_user_id_exists > 0,
    'UPDATE doc_embeddings SET uploader = user_id WHERE uploader IS NULL AND user_id IS NOT NULL',
    'SELECT 1'
);
PREPARE copy_legacy_owner FROM @copy_legacy_owner_sql;
EXECUTE copy_legacy_owner;
DEALLOCATE PREPARE copy_legacy_owner;

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

-- No default administrator is inserted. Set HEALTH_ADMIN_USERNAME and
-- HEALTH_ADMIN_PASSWORD when starting the application to create or rotate it.
