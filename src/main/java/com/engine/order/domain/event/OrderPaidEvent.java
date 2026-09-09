package com.engine.order.domain.event;

import com.engine.order.domain.model.OrderId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderPaidEvent(
        UUID eventId,
        OrderId orderId,
        String transactionId,
        Instant occurredOn
) implements DomainEvent {

    public OrderPaidEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    }

    public static OrderPaidEvent now(OrderId orderId, String transactionId) {
        return new OrderPaidEvent(UUID.randomUUID(), orderId, transactionId, Instant.now());
    }
}
