package com.engine.order.application.dto;

import com.engine.order.domain.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponseDto(
        UUID id,
        UUID customerId,
        String state,
        String currency,
        BigDecimal totalAmount,
        List<OrderItemResponseDto> items,
        String cancellationReason,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponseDto fromDomain(Order order) {
        List<OrderItemResponseDto> itemDtos = order.getItems().stream()
                .map(OrderItemResponseDto::fromDomain)
                .toList();

        return new OrderResponseDto(
                order.getId().value(),
                order.getCustomerId().value(),
                order.getState().name(),
                order.getTotalAmount().currency().getCurrencyCode(),
                order.getTotalAmount().amount(),
                itemDtos,
                order.getCancellationReason().orElse(null),
                order.getVersion(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
