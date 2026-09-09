package com.engine.order.infrastructure.adapter.in.rest.dto;

import com.engine.order.application.dto.PaymentResultCommand;
import jakarta.validation.constraints.NotBlank;

public record ProcessPaymentRequest(
        @NotBlank(message = "Transaction ID is required")
        String transactionId,

        boolean successful
) {
    public PaymentResultCommand toCommand() {
        return new PaymentResultCommand(transactionId, successful);
    }
}
