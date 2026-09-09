package com.engine.order.application.saga.message;

import java.util.UUID;

public record PaymentRefundedEvent(
        UUID sagaId,
        UUID orderId,
        String transactionId
) {}
