package com.engine.order.domain.port.in;

import com.engine.order.domain.model.Order;
import com.engine.order.domain.model.OrderId;

public interface CancelOrderUseCase {

    Order cancelOrder(OrderId orderId, String reason);
}
