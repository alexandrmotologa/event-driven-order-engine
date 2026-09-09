-- ============================================================================
-- V5: Immutable Event Store for Audit Trail & Time-Travel Debugging
-- ============================================================================

CREATE TABLE IF NOT EXISTS order_event_stream (
    event_id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_order_event_stream_sequence UNIQUE (order_id, sequence_number)
);

CREATE INDEX IF NOT EXISTS idx_event_stream_order_seq ON order_event_stream(order_id, sequence_number ASC);
CREATE INDEX IF NOT EXISTS idx_event_stream_created_at ON order_event_stream(created_at ASC);
