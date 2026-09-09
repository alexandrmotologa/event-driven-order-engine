package com.engine.order.application.port.in;

import com.engine.order.application.dto.OrderSummaryDto;

import java.util.List;
import java.util.Optional;

public interface OrderSummaryQueryPort {
    List<OrderSummaryDto> getOrderSummaries(String customerId, String status);
    Optional<OrderSummaryDto> getOrderSummaryById(String orderId);
}
