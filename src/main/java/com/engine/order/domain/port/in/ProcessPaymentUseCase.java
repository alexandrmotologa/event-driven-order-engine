package com.engine.order.domain.port.in;

import com.engine.order.domain.model.Order;
import com.engine.order.domain.model.OrderId;

public interface ProcessPaymentUseCase {

    Order processPayment(OrderId orderId, String transactionId, boolean successful);
}
