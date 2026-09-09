package com.engine.order.application.dto;

import java.util.Objects;

public record PaymentResultCommand(
        String transactionId,
        boolean successful
) {
    public PaymentResultCommand {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        if (transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId must not be blank");
        }
    }
}
