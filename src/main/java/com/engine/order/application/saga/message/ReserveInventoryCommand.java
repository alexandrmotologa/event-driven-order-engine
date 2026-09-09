package com.engine.order.application.saga.message;

import com.engine.order.application.dto.OrderItemCommand;

import java.util.List;
import java.util.UUID;

public record ReserveInventoryCommand(
        UUID sagaId,
        UUID orderId,
        List<OrderItemCommand> items
) {}
