package com.engine.order.application.dto;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderItemCommand(
        String productSku,
        int quantity,
        BigDecimal unitPrice
) {
    public OrderItemCommand {
        Objects.requireNonNull(productSku, "productSku must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        if (productSku.isBlank()) {
            throw new IllegalArgumentException("productSku must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
