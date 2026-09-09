package com.engine.order.application.dto;

import java.time.Instant;

public record OrderEventStreamRecord(
        String eventId,
        String orderId,
        long sequenceNumber,
        String eventType,
        String payload,
        String metadata,
        Instant createdAt
) {
}
