package com.engine.order.application.saga.message;

import java.util.UUID;

public record PaymentAuthorizedEvent(
        UUID sagaId,
        UUID orderId,
        String transactionId
) {}
