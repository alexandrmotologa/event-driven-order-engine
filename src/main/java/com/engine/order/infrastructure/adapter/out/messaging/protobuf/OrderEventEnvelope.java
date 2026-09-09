package com.engine.order.infrastructure.adapter.out.messaging.protobuf;

public record OrderEventEnvelope(
        String eventId,
        String orderId,
        String eventType,
        long sequenceNumber,
        long timestamp,
        byte[] payload
) {
}
