-- ============================================================================
-- V6: Dead Letter Queue (DLQ) Management & Poison Pill Reprocessing
-- ============================================================================

CREATE TABLE IF NOT EXISTS dlq_messages (
    id VARCHAR(64) PRIMARY KEY,
    original_topic VARCHAR(128) NOT NULL,
    partition_num INT NOT NULL,
    offset_num BIGINT NOT NULL,
    message_key VARCHAR(128),
    payload TEXT NOT NULL,
    exception_class VARCHAR(256),
    error_message TEXT,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dlq_status ON dlq_messages(status);
CREATE INDEX IF NOT EXISTS idx_dlq_topic ON dlq_messages(original_topic);
CREATE INDEX IF NOT EXISTS idx_dlq_created_at ON dlq_messages(created_at DESC);
