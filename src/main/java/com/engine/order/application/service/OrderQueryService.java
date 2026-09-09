package com.engine.order.application.service;

import com.engine.order.application.dto.OrderResponseDto;
import com.engine.order.domain.exception.OrderNotFoundException;
import com.engine.order.domain.model.Order;
import com.engine.order.domain.model.OrderId;
import com.engine.order.domain.port.in.GetOrderQuery;
import com.engine.order.domain.port.out.OrderRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrderQueryService implements GetOrderQuery {

    private final OrderRepositoryPort orderRepositoryPort;

    public OrderQueryService(OrderRepositoryPort orderRepositoryPort) {
        this.orderRepositoryPort = Objects.requireNonNull(orderRepositoryPort, "orderRepositoryPort must not be null");
    }

    public OrderResponseDto handleGetOrder(UUID orderIdValue) {
        OrderId orderId = OrderId.of(orderIdValue);
        Order order = getOrder(orderId);
        return OrderResponseDto.fromDomain(order);
    }

    @Override
    public Order getOrder(OrderId orderId) {
        return orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
