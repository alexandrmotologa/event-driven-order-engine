package com.engine.order.domain.event;

import com.engine.order.domain.model.CustomerId;
import com.engine.order.domain.model.Money;
import com.engine.order.domain.model.OrderId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        OrderId orderId,
        CustomerId customerId,
        Money totalAmount,
        Instant occurredOn
) implements DomainEvent {

    public OrderCreatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    }

    public static OrderCreatedEvent now(OrderId orderId, CustomerId customerId, Money totalAmount) {
        return new OrderCreatedEvent(UUID.randomUUID(), orderId, customerId, totalAmount, Instant.now());
    }
}
