package com.engine.order.application.saga.message;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundPaymentCommand(
        UUID sagaId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String reason
) {}
