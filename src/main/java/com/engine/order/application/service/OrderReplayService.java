package com.engine.order.application.service;

import com.engine.order.application.dto.OrderEventStreamRecord;
import com.engine.order.application.dto.OrderResponseDto;
import com.engine.order.application.port.in.OrderHistoryUseCase;
import com.engine.order.application.port.out.EventStorePort;
import com.engine.order.domain.exception.OrderNotFoundException;
import com.engine.order.domain.model.*;
import com.engine.order.domain.port.out.OrderRepositoryPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class OrderReplayService implements OrderHistoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderReplayService.class);

    private final EventStorePort eventStorePort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final ObjectMapper objectMapper;

    public OrderReplayService(
            EventStorePort eventStorePort,
            OrderRepositoryPort orderRepositoryPort,
            ObjectMapper objectMapper
    ) {
        this.eventStorePort = Objects.requireNonNull(eventStorePort, "eventStorePort must not be null");
        this.orderRepositoryPort = Objects.requireNonNull(orderRepositoryPort, "orderRepositoryPort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<OrderEventStreamRecord> getAuditTrail(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        return eventStorePort.getEventsForOrder(orderId);
    }

    @Override
    public OrderResponseDto replayOrderToVersion(UUID orderId, long targetVersion) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (targetVersion < 1) {
            throw new IllegalArgumentException("targetVersion must be greater than or equal to 1");
        }

        List<OrderEventStreamRecord> events = eventStorePort.getEventsForOrderUpTo(orderId, targetVersion);
        if (events.isEmpty()) {
            throw new OrderNotFoundException(OrderId.of(orderId));
        }

        Optional<Order> originalOrderOpt = orderRepositoryPort.findById(OrderId.of(orderId));

        OrderId id = OrderId.of(orderId);
        CustomerId customerId = originalOrderOpt.map(Order::getCustomerId).orElse(null);
        List<OrderItem> items = originalOrderOpt.map(Order::getItems).orElse(Collections.emptyList());
        Money totalAmount = originalOrderOpt.map(Order::getTotalAmount).orElse(null);
        Instant createdAt = originalOrderOpt.map(Order::getCreatedAt).orElse(events.get(0).createdAt());
        Instant updatedAt = events.get(events.size() - 1).createdAt();

        OrderState state = OrderState.CREATED;
        String cancellationReason = null;
        long currentVersion = 0L;

        for (OrderEventStreamRecord record : events) {
            currentVersion = record.sequenceNumber();
            updatedAt = record.createdAt();
            String eventType = record.eventType();

            switch (eventType) {
                case "OrderCreatedEvent" -> {
                    state = OrderState.CREATED;
                    if (customerId == null || totalAmount == null) {
                        try {
                            JsonNode root = objectMapper.readTree(record.payload());
                            if (customerId == null && root.has("customerId")) {
                                customerId = CustomerId.of(UUID.fromString(root.get("customerId").get("value").asText()));
                            }
                            if (totalAmount == null && root.has("totalAmount")) {
                                JsonNode moneyNode = root.get("totalAmount");
                                BigDecimal amt = new BigDecimal(moneyNode.get("amount").asText());
                                String currencyCode = "USD";
                                if (moneyNode.has("currency")) {
                                    JsonNode cNode = moneyNode.get("currency");
                                    currencyCode = cNode.isObject() && cNode.has("currencyCode")
                                            ? cNode.get("currencyCode").asText()
                                            : cNode.asText();
                                }
                                totalAmount = Money.of(amt, currencyCode);
                            }
                        } catch (Exception e) {
                            log.warn("Could not parse payload for OrderCreatedEvent", e);
                        }
                    }
                }
                case "OrderValidatedEvent" -> state = OrderState.VALIDATED;
                case "OrderPaymentPendingEvent" -> state = OrderState.PAYMENT_PENDING;
                case "OrderPaidEvent" -> state = OrderState.PAID;
                case "OrderInventoryAllocatedEvent" -> state = OrderState.INVENTORY_ALLOCATED;
                case "OrderCompletedEvent" -> state = OrderState.COMPLETED;
                case "OrderCancelledEvent" -> {
                    state = OrderState.CANCELLED;
                    try {
                        JsonNode root = objectMapper.readTree(record.payload());
                        if (root.has("reason")) {
                            cancellationReason = root.get("reason").asText();
                        }
                    } catch (Exception e) {
                        cancellationReason = "Cancelled";
                    }
                }
                case "OrderRefundedEvent" -> {
                    state = OrderState.REFUNDED;
                    try {
                        JsonNode root = objectMapper.readTree(record.payload());
                        if (root.has("reason")) {
                            cancellationReason = root.get("reason").asText();
                        }
                    } catch (Exception e) {
                        cancellationReason = "Refunded";
                    }
                }
                default -> log.warn("Encountered unhandled event type during replay: {}", eventType);
            }
        }

        if (customerId == null) {
            customerId = CustomerId.of(UUID.randomUUID());
        }
        if (totalAmount == null) {
            totalAmount = Money.of(BigDecimal.ZERO, "USD");
        }

        Order replayedOrder = Order.reconstitute(
                id,
                customerId,
                state,
                items,
                totalAmount,
                cancellationReason,
                currentVersion,
                createdAt,
                updatedAt
        );

        return OrderResponseDto.fromDomain(replayedOrder);
    }
}
