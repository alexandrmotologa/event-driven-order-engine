package com.engine.order.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelOrderRequest(
        @NotBlank(message = "Cancellation reason is required")
        String reason
) {}
