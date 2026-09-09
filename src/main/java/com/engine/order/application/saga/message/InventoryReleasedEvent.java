package com.engine.order.application.saga.message;

import java.util.UUID;

public record InventoryReleasedEvent(
        UUID sagaId,
        UUID orderId
) {}
