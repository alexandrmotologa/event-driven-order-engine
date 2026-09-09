package com.engine.order.domain.port.in;

import com.engine.order.domain.model.CustomerId;
import com.engine.order.domain.model.Order;
import com.engine.order.domain.model.OrderItem;

import java.util.List;

public interface CreateOrderUseCase {

    Order createOrder(CustomerId customerId, List<OrderItem> items);
}
