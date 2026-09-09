-- V3__init_saga_and_dlq_schema.sql
-- Saga Instances and Dead Letter Queue (DLQ) Schema

CREATE TABLE IF NOT EXISTS saga_instances (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    saga_name VARCHAR(64) NOT NULL,
    current_step VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    payload TEXT,
    error_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS dlq_messages (
    id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    partition_id INT,
    offset_id BIGINT,
    payload TEXT NOT NULL,
    error_reason TEXT,
    failed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_saga_instances_order_id ON saga_instances (order_id);
CREATE INDEX IF NOT EXISTS idx_saga_instances_status ON saga_instances (status);
CREATE INDEX IF NOT EXISTS idx_dlq_messages_topic ON dlq_messages (topic);
