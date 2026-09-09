package com.engine.order.application.dto;

import com.engine.order.domain.model.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponseDto(
        UUID id,
        String productSku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public static OrderItemResponseDto fromDomain(OrderItem item) {
        return new OrderItemResponseDto(
                item.getId(),
                item.getProductSku(),
                item.getQuantity(),
                item.getUnitPrice().amount(),
                item.getSubtotal().amount()
        );
    }
}
