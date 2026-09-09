package com.engine.order.application.service;

import com.engine.order.application.dto.OrderSummaryDto;
import com.engine.order.application.port.in.OrderSummaryQueryPort;
import com.engine.order.application.port.out.OrderSummaryViewRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class OrderSummaryQueryService implements OrderSummaryQueryPort {

    private final OrderSummaryViewRepositoryPort repositoryPort;

    public OrderSummaryQueryService(OrderSummaryViewRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

    @Override
    public List<OrderSummaryDto> getOrderSummaries(String customerId, String status) {
        return repositoryPort.findAll(customerId, status);
    }

    @Override
    public Optional<OrderSummaryDto> getOrderSummaryById(String orderId) {
        return repositoryPort.findById(orderId);
    }
}
