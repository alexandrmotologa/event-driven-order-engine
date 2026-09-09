package com.engine.order.application.port.in;

import com.engine.order.application.dto.OrderEventStreamRecord;
import com.engine.order.application.dto.OrderResponseDto;

import java.util.List;
import java.util.UUID;

public interface OrderHistoryUseCase {

    List<OrderEventStreamRecord> getAuditTrail(UUID orderId);

    OrderResponseDto replayOrderToVersion(UUID orderId, long targetVersion);
}
