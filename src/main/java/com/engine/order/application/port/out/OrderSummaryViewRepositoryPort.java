package com.engine.order.application.port.out;

import com.engine.order.application.dto.OrderSummaryDto;

import java.util.List;
import java.util.Optional;

public interface OrderSummaryViewRepositoryPort {
    void save(OrderSummaryDto summary);
    Optional<OrderSummaryDto> findById(String orderId);
    List<OrderSummaryDto> findAll(String customerId, String status);
}
