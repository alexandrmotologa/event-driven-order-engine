package com.engine.order.domain.exception;

import com.engine.order.domain.model.OrderId;

public class OrderNotFoundException extends DomainException {

    private final OrderId orderId;

    public OrderNotFoundException(OrderId orderId) {
        super(String.format("Order not found with ID: %s", orderId != null ? orderId.value() : "null"));
        this.orderId = orderId;
    }

    public OrderId getOrderId() {
        return orderId;
    }
}
