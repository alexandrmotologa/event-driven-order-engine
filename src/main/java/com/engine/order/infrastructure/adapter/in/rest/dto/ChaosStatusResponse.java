package com.engine.order.infrastructure.adapter.in.rest.dto;

public record ChaosStatusResponse(
        boolean enabled,
        long latencyMs,
        int paymentFailureRate,
        boolean simulatePaymentOutage,
        String circuitBreakerState
) {
}
