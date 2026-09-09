package com.engine.order.infrastructure.adapter.in.rest.dto;

import com.engine.order.application.dto.CreateOrderCommand;
import com.engine.order.application.dto.OrderItemCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotBlank(message = "Currency code is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
        String currency,

        @NotEmpty(message = "Order must contain at least one item")
        @Valid
        List<OrderItemRequest> items
) {
    public CreateOrderCommand toCommand() {
        List<OrderItemCommand> itemCommands = items.stream()
                .map(item -> new OrderItemCommand(item.productSku(), item.quantity(), item.unitPrice()))
                .toList();
        return new CreateOrderCommand(customerId, currency, itemCommands);
    }
}
