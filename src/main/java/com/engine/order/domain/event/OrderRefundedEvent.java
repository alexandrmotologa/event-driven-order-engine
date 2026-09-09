package com.engine.order.domain.event;

import com.engine.order.domain.model.OrderId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderRefundedEvent(
        UUID eventId,
        OrderId orderId,
        String reason,
        Instant occurredOn
) implements DomainEvent {

    public OrderRefundedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    }

    public static OrderRefundedEvent now(OrderId orderId, String reason) {
        return new OrderRefundedEvent(UUID.randomUUID(), orderId, reason, Instant.now());
    }
}
