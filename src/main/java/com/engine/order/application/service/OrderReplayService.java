package com.engine.order.application.service;

import com.engine.order.application.dto.OrderEventStreamRecord;
import com.engine.order.application.dto.OrderResponseDto;
import com.engine.order.application.dto.OrderSnapshotRecord;
import com.engine.order.application.port.in.OrderHistoryUseCase;
import com.engine.order.application.port.out.EventStorePort;
import com.engine.order.application.port.out.SnapshotPort;
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
    private final SnapshotPort snapshotPort;
    private final ObjectMapper objectMapper;

    public OrderReplayService(
            EventStorePort eventStorePort,
            OrderRepositoryPort orderRepositoryPort,
            SnapshotPort snapshotPort,
            ObjectMapper objectMapper
    ) {
        this.eventStorePort = Objects.requireNonNull(eventStorePort, "eventStorePort must not be null");
        this.orderRepositoryPort = Objects.requireNonNull(orderRepositoryPort, "orderRepositoryPort must not be null");
        this.snapshotPort = Objects.requireNonNull(snapshotPort, "snapshotPort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<OrderEventStreamRecord> getAuditTrail(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        return eventStorePort.getEventsForOrder(orderId);
    }

    @Override
    public List<OrderSnapshotRecord> getSnapshots(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        return snapshotPort.getSnapshotsForOrder(orderId);
    }

    @Override
    @Transactional
    public void createSnapshot(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Order order = orderRepositoryPort.findById(OrderId.of(orderId))
                .orElseThrow(() -> new OrderNotFoundException(OrderId.of(orderId)));

        try {
            long currentSeq = eventStorePort.getNextSequenceNumber(orderId) - 1;
            if (currentSeq < 1) {
                currentSeq = order.getVersion();
            }

            String aggregateState = objectMapper.writeValueAsString(OrderResponseDto.fromDomain(order));
            snapshotPort.saveSnapshot(orderId, currentSeq, order.getState().name(), aggregateState);
            log.info("Snapshotting: Created snapshot for order [{}] at sequence [{}] with state [{}]",
                    orderId, currentSeq, order.getState());
        } catch (Exception e) {
            log.error("Failed to create snapshot for order [{}]", orderId, e);
            throw new RuntimeException("Could not create snapshot for order: " + orderId, e);
        }
    }

    @Override
    public OrderResponseDto replayOrderToVersion(UUID orderId, long targetVersion) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (targetVersion < 1) {
            throw new IllegalArgumentException("targetVersion must be greater than or equal to 1");
        }

        Optional<OrderSnapshotRecord> snapshotOpt = snapshotPort.findLatestSnapshotUpTo(orderId, targetVersion);

        long startingSequence = 1L;
        OrderState state = OrderState.CREATED;
        CustomerId customerId = null;
        List<OrderItem> items = Collections.emptyList();
        Money totalAmount = null;
        String cancellationReason = null;
        Instant createdAt = null;
        Instant updatedAt = null;
        long currentVersion = 0L;

        if (snapshotOpt.isPresent()) {
            OrderSnapshotRecord snapshot = snapshotOpt.get();
            startingSequence = snapshot.snapshotVersion() + 1;
            currentVersion = snapshot.snapshotVersion();
            state = OrderState.valueOf(snapshot.state());
            createdAt = snapshot.createdAt();
            updatedAt = snapshot.createdAt();

            try {
                OrderResponseDto snapshotDto = objectMapper.readValue(snapshot.aggregateState(), OrderResponseDto.class);
                customerId = CustomerId.of(snapshotDto.customerId());
                totalAmount = Money.of(snapshotDto.totalAmount(), snapshotDto.currency());
                cancellationReason = snapshotDto.cancellationReason();
                createdAt = snapshotDto.createdAt();
                updatedAt = snapshotDto.updatedAt();
            } catch (Exception e) {
                log.warn("Could not deserialize snapshot for order [{}], falling back to event replay from start", orderId, e);
                startingSequence = 1L;
            }
        }

        // Fast-path: If the snapshot is exactly at targetVersion, return immediately without loading events! O(1)
        if (startingSequence > targetVersion && snapshotOpt.isPresent()) {
            Optional<Order> orig = orderRepositoryPort.findById(OrderId.of(orderId));
            items = orig.map(Order::getItems).orElse(Collections.emptyList());
            Order hydrated = Order.reconstitute(
                    OrderId.of(orderId),
                    customerId != null ? customerId : CustomerId.of(UUID.randomUUID()),
                    state,
                    items,
                    totalAmount != null ? totalAmount : Money.of(BigDecimal.ZERO, "USD"),
                    cancellationReason,
                    currentVersion,
                    createdAt != null ? createdAt : Instant.now(),
                    updatedAt != null ? updatedAt : Instant.now()
            );
            return OrderResponseDto.fromDomain(hydrated);
        }

        List<OrderEventStreamRecord> events = eventStorePort.getEventsForOrderUpTo(orderId, targetVersion);
        if (events.isEmpty() && snapshotOpt.isEmpty()) {
            throw new OrderNotFoundException(OrderId.of(orderId));
        }

        Optional<Order> originalOrderOpt = orderRepositoryPort.findById(OrderId.of(orderId));
        if (customerId == null) customerId = originalOrderOpt.map(Order::getCustomerId).orElse(null);
        items = originalOrderOpt.map(Order::getItems).orElse(Collections.emptyList());
        if (totalAmount == null) totalAmount = originalOrderOpt.map(Order::getTotalAmount).orElse(null);
        if (createdAt == null) createdAt = originalOrderOpt.map(Order::getCreatedAt).orElse(events.isEmpty() ? Instant.now() : events.get(0).createdAt());

        for (OrderEventStreamRecord record : events) {
            if (record.sequenceNumber() < startingSequence) {
                continue; // Skip events already materialized in snapshot
            }

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
                OrderId.of(orderId),
                customerId,
                state,
                items,
                totalAmount,
                cancellationReason,
                currentVersion,
                createdAt,
                updatedAt != null ? updatedAt : Instant.now()
        );

        return OrderResponseDto.fromDomain(replayedOrder);
    }
}
