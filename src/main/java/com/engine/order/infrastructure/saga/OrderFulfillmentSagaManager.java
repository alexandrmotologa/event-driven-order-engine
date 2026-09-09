package com.engine.order.infrastructure.saga;

import com.engine.order.application.dto.OrderItemCommand;
import com.engine.order.application.saga.message.*;
import com.engine.order.domain.model.Order;
import com.engine.order.domain.model.OrderId;
import com.engine.order.domain.port.out.OrderRepositoryPort;
import com.engine.order.infrastructure.adapter.out.saga.entity.SagaInstanceJpaEntity;
import com.engine.order.infrastructure.adapter.out.saga.entity.SagaStatus;
import com.engine.order.infrastructure.adapter.out.saga.repository.SagaInstanceJpaRepository;
import com.engine.order.infrastructure.config.KafkaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderFulfillmentSagaManager {

    private static final Logger log = LoggerFactory.getLogger(OrderFulfillmentSagaManager.class);

    private final SagaInstanceJpaRepository sagaInstanceJpaRepository;
    private final OrderRepositoryPort orderRepositoryPort;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final com.engine.order.infrastructure.metrics.OrderMetrics orderMetrics;

    public OrderFulfillmentSagaManager(
            SagaInstanceJpaRepository sagaInstanceJpaRepository,
            OrderRepositoryPort orderRepositoryPort,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            com.engine.order.infrastructure.metrics.OrderMetrics orderMetrics
    ) {
        this.sagaInstanceJpaRepository = Objects.requireNonNull(sagaInstanceJpaRepository, "sagaInstanceJpaRepository must not be null");
        this.orderRepositoryPort = Objects.requireNonNull(orderRepositoryPort, "orderRepositoryPort must not be null");
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.orderMetrics = Objects.requireNonNull(orderMetrics, "orderMetrics must not be null");
    }

    @Transactional
    public UUID startSaga(Order order) {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = order.getId().value();

        log.info("SAGA: Starting OrderFulfillmentSaga [{}] for order [{}]", sagaId, orderId);

        SagaInstanceJpaEntity sagaInstance = SagaInstanceJpaEntity.start(
                sagaId,
                orderId,
                "OrderFulfillmentSaga",
                "INVENTORY_RESERVATION",
                null
        );
        sagaInstance.setStatus(SagaStatus.INVENTORY_RESERVATION_PENDING);
        sagaInstanceJpaRepository.save(sagaInstance);

        // Step 1: Send ReserveInventoryCommand
        List<OrderItemCommand> itemCommands = order.getItems().stream()
                .map(item -> new OrderItemCommand(item.getProductSku(), item.getQuantity(), item.getUnitPrice().amount()))
                .toList();

        ReserveInventoryCommand cmd = new ReserveInventoryCommand(sagaId, orderId, itemCommands);
        sendCommand(KafkaConfig.INVENTORY_COMMANDS_TOPIC, "ReserveInventoryCommand", orderId.toString(), cmd);

        return sagaId;
    }

    @KafkaListener(
            topics = KafkaConfig.INVENTORY_REPLIES_TOPIC,
            groupId = "saga-inventory-replies-group"
    )
    @Transactional
    public void onInventoryReply(
            @Payload String payload,
            @Header(name = "event_type", required = false) byte[] eventTypeBytes
    ) {
        String eventType = eventTypeBytes != null ? new String(eventTypeBytes) : "UNKNOWN";
        log.info("SAGA: Received Inventory reply [{}]", eventType);

        try {
            JsonNode root = objectMapper.readTree(payload);
            UUID sagaId = UUID.fromString(root.path("sagaId").asText());
            UUID orderId = UUID.fromString(root.path("orderId").asText());

            SagaInstanceJpaEntity saga = sagaInstanceJpaRepository.findById(sagaId)
                    .orElseGet(() -> sagaInstanceJpaRepository.findByOrderId(orderId).orElse(null));

            if (saga == null) {
                log.warn("SAGA: Instance not found for reply event [{}]", eventType);
                return;
            }

            if ("InventoryReservedEvent".equals(eventType) || (!root.has("reason") && root.has("sagaId"))) {
                log.info("SAGA [{}]: Inventory reserved. Transitioning to Payment Authorization.", sagaId);
                saga.transitionTo("PAYMENT_AUTHORIZATION", SagaStatus.PAYMENT_AUTHORIZATION_PENDING);
                sagaInstanceJpaRepository.save(saga);

                // Transition Order: CREATED -> VALIDATED -> PAYMENT_PENDING
                Order order = orderRepositoryPort.findById(OrderId.of(orderId)).orElseThrow();
                if (order.getState() == com.engine.order.domain.model.OrderState.CREATED) {
                    order = order.validate();
                    orderMetrics.recordStateTransition("CREATED", "VALIDATED");
                }
                if (order.getState() == com.engine.order.domain.model.OrderState.VALIDATED) {
                    order = order.initiatePayment();
                    orderMetrics.recordStateTransition("VALIDATED", "PAYMENT_PENDING");
                }
                orderRepositoryPort.save(order);

                // Step 2: Send AuthorizePaymentCommand
                AuthorizePaymentCommand payCmd = new AuthorizePaymentCommand(
                        sagaId,
                        orderId,
                        order.getTotalAmount().amount(),
                        order.getTotalAmount().currency().getCurrencyCode()
                );
                sendCommand(KafkaConfig.PAYMENT_COMMANDS_TOPIC, "AuthorizePaymentCommand", orderId.toString(), payCmd);

            } else if ("InventoryReservationFailedEvent".equals(eventType) || root.has("reason")) {
                String reason = root.path("reason").asText("Inventory unavailable");
                log.warn("SAGA [{}]: Inventory reservation failed: {}. Compensating saga.", sagaId, reason);
                orderMetrics.recordSagaFailure("OrderFulfillmentSaga", reason);
                saga.markCompensated("Inventory reservation failed: " + reason);
                sagaInstanceJpaRepository.save(saga);

                // Cancel order
                Order order = orderRepositoryPort.findById(OrderId.of(orderId)).orElseThrow();
                if (order.getState().canTransitionTo(com.engine.order.domain.model.OrderState.CANCELLED)) {
                    Order cancelled = order.cancel("Out of stock / Inventory reservation failed: " + reason);
                    orderRepositoryPort.save(cancelled);
                    orderMetrics.recordStateTransition(order.getState().name(), "CANCELLED");
                }
            }
        } catch (Exception ex) {
            log.error("SAGA: Error handling inventory reply", ex);
        }
    }

    @KafkaListener(
            topics = KafkaConfig.PAYMENT_REPLIES_TOPIC,
            groupId = "saga-payment-replies-group"
    )
    @Transactional
    public void onPaymentReply(
            @Payload String payload,
            @Header(name = "event_type", required = false) byte[] eventTypeBytes
    ) {
        String eventType = eventTypeBytes != null ? new String(eventTypeBytes) : "UNKNOWN";
        log.info("SAGA: Received Payment reply [{}]", eventType);

        try {
            JsonNode root = objectMapper.readTree(payload);
            UUID sagaId = UUID.fromString(root.path("sagaId").asText());
            UUID orderId = UUID.fromString(root.path("orderId").asText());

            SagaInstanceJpaEntity saga = sagaInstanceJpaRepository.findById(sagaId)
                    .orElseGet(() -> sagaInstanceJpaRepository.findByOrderId(orderId).orElse(null));

            if (saga == null) {
                log.warn("SAGA: Instance not found for payment reply [{}]", eventType);
                return;
            }

            if ("PaymentAuthorizedEvent".equals(eventType) || (root.has("transactionId") && !root.has("reason"))) {
                String txId = root.path("transactionId").asText("TX-UNKNOWN");
                log.info("SAGA [{}]: Payment authorized with tx [{}]. Completing order.", sagaId, txId);

                // Order transitions: PAYMENT_PENDING -> PAID -> INVENTORY_ALLOCATED -> COMPLETED
                Order order = orderRepositoryPort.findById(OrderId.of(orderId)).orElseThrow();
                order = order.markPaid(txId);
                orderMetrics.recordStateTransition("PAYMENT_PENDING", "PAID");
                order = order.allocateInventory();
                orderMetrics.recordStateTransition("PAID", "INVENTORY_ALLOCATED");
                order = order.complete();
                orderMetrics.recordStateTransition("INVENTORY_ALLOCATED", "COMPLETED");
                orderRepositoryPort.save(order);

                saga.markCompleted();
                sagaInstanceJpaRepository.save(saga);
                log.info("SAGA [{}]: Order [{}] successfully fulfilled and COMPLETED!", sagaId, orderId);

            } else if ("PaymentFailedEvent".equals(eventType) || root.has("reason")) {
                String reason = root.path("reason").asText("Payment declined");
                log.warn("SAGA [{}]: Payment failed: {}. Triggering COMPENSATING TRANSACTIONS.", sagaId, reason);
                orderMetrics.recordSagaFailure("OrderFulfillmentSaga", reason);

                saga.transitionTo("COMPENSATING_INVENTORY", SagaStatus.COMPENSATING_INVENTORY);
                saga.setErrorReason(reason);
                sagaInstanceJpaRepository.save(saga);

                // Compensating Action 1: Release reserved inventory
                ReleaseInventoryCommand releaseCmd = new ReleaseInventoryCommand(
                        sagaId,
                        orderId,
                        "Rollback: Payment authorization failed - " + reason
                );
                sendCommand(KafkaConfig.INVENTORY_COMMANDS_TOPIC, "ReleaseInventoryCommand", orderId.toString(), releaseCmd);

                // Compensating Action 2: Cancel Order
                Order order = orderRepositoryPort.findById(OrderId.of(orderId)).orElseThrow();
                if (order.getState().canTransitionTo(com.engine.order.domain.model.OrderState.CANCELLED)) {
                    Order cancelled = order.cancel("Cancelled due to payment authorization failure: " + reason);
                    orderRepositoryPort.save(cancelled);
                    orderMetrics.recordStateTransition(order.getState().name(), "CANCELLED");
                }

                saga.markCompensated("Payment failed: " + reason);
                sagaInstanceJpaRepository.save(saga);
                log.info("SAGA [{}]: Compensation completed for order [{}]", sagaId, orderId);
            }
        } catch (Exception ex) {
            log.error("SAGA: Error handling payment reply", ex);
        }
    }

    public Optional<SagaInstanceJpaEntity> getSagaByOrderId(UUID orderId) {
        return sagaInstanceJpaRepository.findByOrderId(orderId);
    }

    private void sendCommand(String topic, String commandType, String key, Object command) {
        try {
            String json = objectMapper.writeValueAsString(command);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
            record.headers().add(new RecordHeader("command_type", commandType.getBytes()));
            kafkaTemplate.send(record);
            log.info("SAGA: Dispatched command [{}] to topic [{}] for key [{}]", commandType, topic, key);
        } catch (Exception ex) {
            log.error("SAGA: Failed to dispatch command [{}] to topic [{}]", commandType, topic, ex);
        }
    }
}
