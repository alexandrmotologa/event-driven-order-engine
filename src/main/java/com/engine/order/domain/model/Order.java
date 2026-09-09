package com.engine.order.domain.model;

import com.engine.order.domain.event.*;
import com.engine.order.domain.exception.InvalidOrderException;
import com.engine.order.domain.exception.InvalidStateTransitionException;

import java.time.Instant;
import java.util.*;

public final class Order {

    private final OrderId id;
    private final CustomerId customerId;
    private final OrderState state;
    private final List<OrderItem> items;
    private final Money totalAmount;
    private final String cancellationReason;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<DomainEvent> domainEvents;

    private Order(
            OrderId id,
            CustomerId customerId,
            OrderState state,
            List<OrderItem> items,
            Money totalAmount,
            String cancellationReason,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<DomainEvent> domainEvents
    ) {
        this.id = Objects.requireNonNull(id, "OrderId must not be null");
        this.customerId = Objects.requireNonNull(customerId, "CustomerId must not be null");
        this.state = Objects.requireNonNull(state, "OrderState must not be null");
        this.items = List.copyOf(Objects.requireNonNull(items, "Items must not be null"));
        this.totalAmount = Objects.requireNonNull(totalAmount, "TotalAmount must not be null");
        this.cancellationReason = cancellationReason;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt must not be null");
        this.domainEvents = List.copyOf(Objects.requireNonNull(domainEvents, "DomainEvents must not be null"));
    }

    public static Order create(OrderId id, CustomerId customerId, List<OrderItem> items) {
        Objects.requireNonNull(id, "OrderId must not be null");
        Objects.requireNonNull(customerId, "CustomerId must not be null");
        if (items == null || items.isEmpty()) {
            throw new InvalidOrderException("Order must contain at least one order item.");
        }

        Money total = calculateTotal(items);
        Instant now = Instant.now();
        DomainEvent createdEvent = OrderCreatedEvent.now(id, customerId, total);

        return new Order(
                id,
                customerId,
                OrderState.CREATED,
                items,
                total,
                null,
                0L,
                now,
                now,
                List.of(createdEvent)
        );
    }

    public static Order reconstitute(
            OrderId id,
            CustomerId customerId,
            OrderState state,
            List<OrderItem> items,
            Money totalAmount,
            String cancellationReason,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Order(
                id,
                customerId,
                state,
                items,
                totalAmount,
                cancellationReason,
                version,
                createdAt,
                updatedAt,
                List.of()
        );
    }

    public Order validate() {
        assertCanTransitionTo(OrderState.VALIDATED);
        Instant now = Instant.now();
        List<DomainEvent> events = appendEvent(OrderValidatedEvent.now(this.id));
        return new Order(
                this.id,
                this.customerId,
                OrderState.VALIDATED,
                this.items,
                this.totalAmount,
                this.cancellationReason,
                this.version,
                this.createdAt,
                now,
                events
        );
    }

    public Order initiatePayment() {
        assertCanTransitionTo(OrderState.PAYMENT_PENDING);
        Instant now = Instant.now();
        List<DomainEvent> events = appendEvent(OrderPaymentPendingEvent.now(this.id));
        return new Order(
                this.id,
                this.customerId,
                OrderState.PAYMENT_PENDING,
                this.items,
                this.totalAmount,
                this.cancellationReason,
                this.version,
                this.createdAt,
                now,
                events
        );
    }

    public Order markPaid(String transactionId) {
        assertCanTransitionTo(OrderState.PAID);
        if (transactionId == null || transactionId.isBlank()) {
            throw new InvalidOrderException("Transaction ID is required when marking order as paid.");
        }
        Instant now = Instant.now();
        List<DomainEvent> events = appendEvent(OrderPaidEvent.now(this.id, transactionId.trim()));
        return new Order(
                this.id,
                this.customerId,
                OrderState.PAID,
                this.items,
                this.totalAmount,
                this.cancellationReason,
                this.version,
                this.createdAt,
                now,
                events
        );
    }

    public Order allocateInventory() {
        assertCanTransitionTo(OrderState.INVENTORY_ALLOCATED);
        Instant now = Instant.now();
        List<DomainEvent> events = appendEvent(OrderInventoryAllocatedEvent.now(this.id));
        return new Order(
                this.id,
                this.customerId,
                OrderState.INVENTORY_ALLOCATED,
                this.items,
                this.totalAmount,
                this.cancellationReason,
                this.version,
                this.createdAt,
                now,
                events
        );
    }

    public Order complete() {
        assertCanTransitionTo(OrderState.COMPLETED);
        Instant now = Instant.now();
        List<DomainEvent> events = appendEvent(OrderCompletedEvent.now(this.id));
        return new Order(
                this.id,
                this.customerId,
                OrderState.COMPLETED,
                this.items,
                this.totalAmount,
                this.cancellationReason,
                this.version,
                this.createdAt,
                now,
                events
        );
    }

    public Order cancel(String reason) {
        assertCanTransitionTo(OrderState.CANCELLED);
        if (reason == null || reason.isBlank()) {
            throw new InvalidOrderException("Cancellation reason must not be empty.");
        }
        Instant now = Instant.now();
        List<DomainEvent> events = appendEvent(OrderCancelledEvent.now(this.id, reason.trim()));
        return new Order(
                this.id,
                this.customerId,
                OrderState.CANCELLED,
                this.items,
                this.totalAmount,
                reason.trim(),
                this.version,
                this.createdAt,
                now,
                events
        );
    }

    public Order refund(String reason) {
        assertCanTransitionTo(OrderState.REFUNDED);
        if (reason == null || reason.isBlank()) {
            throw new InvalidOrderException("Refund reason must not be empty.");
        }
        Instant now = Instant.now();
        List<DomainEvent> events = appendEvent(OrderRefundedEvent.now(this.id, reason.trim()));
        return new Order(
                this.id,
                this.customerId,
                OrderState.REFUNDED,
                this.items,
                this.totalAmount,
                reason.trim(),
                this.version,
                this.createdAt,
                now,
                events
        );
    }

    public Order clearDomainEvents() {
        return new Order(
                this.id,
                this.customerId,
                this.state,
                this.items,
                this.totalAmount,
                this.cancellationReason,
                this.version,
                this.createdAt,
                this.updatedAt,
                List.of()
        );
    }

    private void assertCanTransitionTo(OrderState target) {
        if (!this.state.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(this.id, this.state, target);
        }
    }

    private List<DomainEvent> appendEvent(DomainEvent newEvent) {
        List<DomainEvent> newEvents = new ArrayList<>(this.domainEvents);
        newEvents.add(newEvent);
        return Collections.unmodifiableList(newEvents);
    }

    private static Money calculateTotal(List<OrderItem> items) {
        Currency commonCurrency = items.getFirst().getUnitPrice().currency();
        Money total = Money.zero(commonCurrency);
        for (OrderItem item : items) {
            if (!item.getUnitPrice().currency().equals(commonCurrency)) {
                throw new InvalidOrderException("All items in an order must use the same currency: " + commonCurrency);
            }
            total = total.add(item.getSubtotal());
        }
        return total;
    }

    public OrderId getId() {
        return id;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public OrderState getState() {
        return state;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public Optional<String> getCancellationReason() {
        return Optional.ofNullable(cancellationReason);
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<DomainEvent> getDomainEvents() {
        return domainEvents;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order order)) return false;
        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", customerId=" + customerId +
                ", state=" + state +
                ", totalAmount=" + totalAmount +
                ", version=" + version +
                '}';
    }
}
