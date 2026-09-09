-- Flyway migration: V7__init_snapshots.sql
-- Snapshotting pattern store for Event Sourcing aggregate reconstitution

CREATE TABLE IF NOT EXISTS order_snapshots (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    snapshot_version BIGINT NOT NULL,
    state VARCHAR(50) NOT NULL,
    aggregate_state TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_order_snapshot_version UNIQUE (order_id, snapshot_version)
);

CREATE INDEX IF NOT EXISTS idx_snapshots_order_version ON order_snapshots (order_id, snapshot_version DESC);
