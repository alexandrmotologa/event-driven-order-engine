package com.engine.order.application.port.out;

import com.engine.order.application.dto.OrderEventStreamRecord;

import java.util.List;
import java.util.UUID;

public interface EventStorePort {
    void append(UUID eventId, UUID orderId, long sequenceNumber, String eventType, String payload, String metadata);
    List<OrderEventStreamRecord> getEventsForOrder(UUID orderId);
    List<OrderEventStreamRecord> getEventsForOrderUpTo(UUID orderId, long maxSequenceNumber);
    long getNextSequenceNumber(UUID orderId);
}
