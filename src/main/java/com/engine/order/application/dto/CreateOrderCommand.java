package com.engine.order.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreateOrderCommand(
        UUID customerId,
        String currency,
        List<OrderItemCommand> items
) {
    public CreateOrderCommand {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(items, "items must not be null");
        if (currency.isBlank() || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a valid 3-letter ISO code");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must contain at least one element");
        }
        items = List.copyOf(items);
    }
}
