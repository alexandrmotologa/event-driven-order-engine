package com.engine.order.application.dto;

import java.time.Instant;

public record OrderSnapshotRecord(
        String id,
        String orderId,
        long snapshotVersion,
        String state,
        String aggregateState,
        Instant createdAt
) {
}
