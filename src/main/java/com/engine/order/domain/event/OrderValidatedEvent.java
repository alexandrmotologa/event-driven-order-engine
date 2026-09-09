package com.engine.order.domain.event;

import com.engine.order.domain.model.OrderId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderValidatedEvent(
        UUID eventId,
        OrderId orderId,
        Instant occurredOn
) implements DomainEvent {

    public OrderValidatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    }

    public static OrderValidatedEvent now(OrderId orderId) {
        return new OrderValidatedEvent(UUID.randomUUID(), orderId, Instant.now());
    }
}
