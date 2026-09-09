package com.engine.order.domain.port.out;

import com.engine.order.domain.model.Order;
import com.engine.order.domain.model.OrderId;

import java.util.Optional;

public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(OrderId orderId);

    boolean existsById(OrderId orderId);
}
