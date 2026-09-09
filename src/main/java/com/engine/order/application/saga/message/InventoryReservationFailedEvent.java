package com.engine.order.application.saga.message;

import java.util.UUID;

public record InventoryReservationFailedEvent(
        UUID sagaId,
        UUID orderId,
        String reason
) {}
