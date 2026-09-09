package com.engine.order.domain.exception;

import com.engine.order.domain.model.OrderId;
import com.engine.order.domain.model.OrderState;

public class InvalidStateTransitionException extends DomainException {

    private final OrderId orderId;
    private final OrderState fromState;
    private final OrderState toState;

    public InvalidStateTransitionException(OrderId orderId, OrderState fromState, OrderState toState) {
        super(String.format("Cannot transition order %s from state [%s] to [%s]",
                orderId != null ? orderId.value() : "UNKNOWN", fromState, toState));
        this.orderId = orderId;
        this.fromState = fromState;
        this.toState = toState;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public OrderState getFromState() {
        return fromState;
    }

    public OrderState getToState() {
        return toState;
    }
}
