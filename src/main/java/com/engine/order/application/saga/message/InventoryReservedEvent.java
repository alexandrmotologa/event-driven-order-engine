package com.engine.order.application.saga.message;

import java.util.UUID;

public record InventoryReservedEvent(
        UUID sagaId,
        UUID orderId
) {}
