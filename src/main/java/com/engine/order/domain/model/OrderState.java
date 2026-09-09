package com.engine.order.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum OrderState {
    CREATED,
    VALIDATED,
    PAYMENT_PENDING,
    PAID,
    INVENTORY_ALLOCATED,
    COMPLETED,
    CANCELLED,
    REFUNDED;

    private static final Set<OrderState> TERMINAL_STATES = EnumSet.of(COMPLETED, CANCELLED, REFUNDED);
    private static final Set<OrderState> CANCELLABLE_STATES = EnumSet.of(CREATED, VALIDATED, PAYMENT_PENDING);

    public boolean canTransitionTo(OrderState target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case CREATED -> target == VALIDATED || target == CANCELLED;
            case VALIDATED -> target == PAYMENT_PENDING || target == CANCELLED;
            case PAYMENT_PENDING -> target == PAID || target == CANCELLED;
            case PAID -> target == INVENTORY_ALLOCATED || target == REFUNDED;
            case INVENTORY_ALLOCATED -> target == COMPLETED;
            case COMPLETED, CANCELLED, REFUNDED -> false;
        };
    }

    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    public boolean isCancellable() {
        return CANCELLABLE_STATES.contains(this);
    }

    public boolean isRefundable() {
        return this == PAID;
    }
}
