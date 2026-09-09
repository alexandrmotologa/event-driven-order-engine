package com.engine.order.application.saga.message;

import java.math.BigDecimal;
import java.util.UUID;

public record AuthorizePaymentCommand(
        UUID sagaId,
        UUID orderId,
        BigDecimal amount,
        String currency
) {}
