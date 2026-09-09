-- ============================================================================
-- V4: CQRS Denormalized Read Model (order_summary_view)
-- ============================================================================

CREATE TABLE IF NOT EXISTS order_summary_view (
    order_id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    item_count INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_event_type VARCHAR(64),
    saga_status VARCHAR(32)
);

CREATE INDEX IF NOT EXISTS idx_order_summary_customer ON order_summary_view(customer_id);
CREATE INDEX IF NOT EXISTS idx_order_summary_status ON order_summary_view(status);
CREATE INDEX IF NOT EXISTS idx_order_summary_created_at ON order_summary_view(created_at DESC);
