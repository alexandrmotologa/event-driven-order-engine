package com.engine.order.domain.event;

import com.engine.order.domain.model.OrderId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderInventoryAllocatedEvent(
        UUID eventId,
        OrderId orderId,
        Instant occurredOn
) implements DomainEvent {

    public OrderInventoryAllocatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    }

    public static OrderInventoryAllocatedEvent now(OrderId orderId) {
        return new OrderInventoryAllocatedEvent(UUID.randomUUID(), orderId, Instant.now());
    }
}
