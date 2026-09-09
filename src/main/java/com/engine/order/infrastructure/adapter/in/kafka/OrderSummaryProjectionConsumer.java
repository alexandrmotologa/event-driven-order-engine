package com.engine.order.infrastructure.adapter.in.kafka;

import com.engine.order.infrastructure.adapter.out.persistence.entity.OrderSummaryViewJpaEntity;
import com.engine.order.infrastructure.adapter.out.persistence.repository.OrderSummaryViewJpaRepository;
import com.engine.order.infrastructure.config.KafkaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Component
public class OrderSummaryProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderSummaryProjectionConsumer.class);

    private final OrderSummaryViewJpaRepository repository;
    private final ObjectMapper objectMapper;

    public OrderSummaryProjectionConsumer(
            OrderSummaryViewJpaRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @KafkaListener(
            topics = KafkaConfig.ORDER_EVENTS_TOPIC,
            groupId = "order-summary-projection-group"
    )
    @Transactional
    public void consume(
            @Payload String payload,
            @Header(name = "event_type", required = false) byte[] eventTypeBytes
    ) {
        String eventType = eventTypeBytes != null ? new String(eventTypeBytes) : "UNKNOWN";
        log.info("OrderSummaryProjectionConsumer received event [{}]", eventType);

        try {
            JsonNode root = objectMapper.readTree(payload);
            String orderId = extractOrderId(root);
            if (orderId == null) {
                log.warn("Skipping event with null orderId from payload: {}", payload);
                return;
            }

            Instant eventTime = extractEventTime(root);
            Optional<OrderSummaryViewJpaEntity> existingOpt = repository.findById(orderId);

            if ("OrderCreatedEvent".equals(eventType)) {
                String customerId = root.has("customerId") && root.get("customerId").has("value")
                        ? root.get("customerId").get("value").asText()
                        : "UNKNOWN_CUSTOMER";

                BigDecimal totalAmount = BigDecimal.ZERO;
                String currency = "USD";
                if (root.has("totalAmount")) {
                    JsonNode moneyNode = root.get("totalAmount");
                    if (moneyNode.has("amount")) {
                        totalAmount = new BigDecimal(moneyNode.get("amount").asText());
                    }
                    if (moneyNode.has("currency")) {
                        currency = moneyNode.get("currency").asText();
                    }
                }

                OrderSummaryViewJpaEntity entity;
                if (existingOpt.isPresent()) {
                    entity = existingOpt.get();
                    entity.setCustomerId(customerId);
                    entity.setTotalAmount(totalAmount);
                    entity.setCurrency(currency);
                } else {
                    entity = new OrderSummaryViewJpaEntity(
                            orderId,
                            customerId,
                            "CREATED",
                            totalAmount,
                            currency,
                            1,
                            eventTime,
                            eventTime,
                            eventType,
                            "STARTED"
                    );
                }

                entity.setStatus("CREATED");
                entity.setLastEventType(eventType);
                entity.setUpdatedAt(eventTime);
                entity.setSagaStatus("STARTED");
                repository.save(entity);
                log.info("CQRS Projection: Saved initial order summary for [{}]", orderId);
            } else if (existingOpt.isPresent()) {
                OrderSummaryViewJpaEntity entity = existingOpt.get();
                entity.setLastEventType(eventType);
                entity.setUpdatedAt(eventTime);

                switch (eventType) {
                    case "OrderValidatedEvent" -> entity.setStatus("VALIDATED");
                    case "OrderPaymentPendingEvent" -> entity.setStatus("PAYMENT_PENDING");
                    case "OrderPaidEvent" -> entity.setStatus("PAID");
                    case "OrderInventoryAllocatedEvent" -> entity.setStatus("INVENTORY_ALLOCATED");
                    case "OrderCompletedEvent" -> {
                        entity.setStatus("COMPLETED");
                        entity.setSagaStatus("COMPLETED");
                    }
                    case "OrderCancelledEvent" -> {
                        entity.setStatus("CANCELLED");
                        entity.setSagaStatus("COMPENSATED");
                    }
                    case "OrderRefundedEvent" -> entity.setStatus("REFUNDED");
                    default -> log.debug("Unhandled event type for summary update: {}", eventType);
                }

                repository.save(entity);
                log.info("CQRS Projection: Updated summary for order [{}] to status [{}]", orderId, entity.getStatus());
            }
        } catch (Exception ex) {
            log.error("Failed to process event for CQRS read model: {}", payload, ex);
        }
    }

    private String extractOrderId(JsonNode root) {
        if (root.has("orderId")) {
            JsonNode orderIdNode = root.get("orderId");
            if (orderIdNode.isObject() && orderIdNode.has("value")) {
                return orderIdNode.get("value").asText();
            } else if (orderIdNode.isTextual()) {
                return orderIdNode.asText();
            }
        }
        return null;
    }

    private Instant extractEventTime(JsonNode root) {
        if (root.has("occurredOn")) {
            try {
                return Instant.parse(root.get("occurredOn").asText());
            } catch (Exception ignored) {
            }
        }
        return Instant.now();
    }
}
