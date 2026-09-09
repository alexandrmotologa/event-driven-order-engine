package com.engine.order.infrastructure.adapter.in.rest.dto;

public record ChaosConfigRequest(
        Boolean enabled,
        Long latencyMs,
        Integer paymentFailureRate,
        Boolean simulatePaymentOutage
) {
}
