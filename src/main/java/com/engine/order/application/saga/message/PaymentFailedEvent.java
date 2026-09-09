package com.engine.order.application.saga.message;

import java.util.UUID;

public record PaymentFailedEvent(
        UUID sagaId,
        UUID orderId,
        String reason
) {}
