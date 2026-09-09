package com.engine.order.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryDto(
        String orderId,
        String customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        int itemCount,
        Instant createdAt,
        Instant updatedAt,
        String lastEventType,
        String sagaStatus
) {
}
