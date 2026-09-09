package com.engine.order.domain.event;

import com.engine.order.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

public sealed interface DomainEvent
        permits OrderCreatedEvent,
                OrderValidatedEvent,
                OrderPaymentPendingEvent,
                OrderPaidEvent,
                OrderInventoryAllocatedEvent,
                OrderCompletedEvent,
                OrderCancelledEvent,
                OrderRefundedEvent {

    UUID eventId();

    OrderId orderId();

    Instant occurredOn();
}
