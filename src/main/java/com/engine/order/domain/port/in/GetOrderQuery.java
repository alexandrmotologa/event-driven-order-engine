package com.engine.order.domain.port.in;

import com.engine.order.domain.model.Order;
import com.engine.order.domain.model.OrderId;

public interface GetOrderQuery {

    Order getOrder(OrderId orderId);
}
