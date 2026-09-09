package com.engine.order.infrastructure.adapter.in.kafka.simulator;

import com.engine.order.application.saga.message.*;
import com.engine.order.infrastructure.config.KafkaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SimulatedInventoryService {

    private static final Logger log = LoggerFactory.getLogger(SimulatedInventoryService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public SimulatedInventoryService(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @KafkaListener(
            topics = KafkaConfig.INVENTORY_COMMANDS_TOPIC,
            groupId = "simulated-inventory-group"
    )
    public void onInventoryCommand(
            @Payload String payload,
            @Header(name = "command_type", required = false) byte[] commandTypeBytes
    ) {
        String commandType = commandTypeBytes != null ? new String(commandTypeBytes) : "UNKNOWN";
        log.info("Simulated Inventory Service: Received command [{}]", commandType);

        try {
            JsonNode root = objectMapper.readTree(payload);
            String sagaIdStr = root.path("sagaId").asText();
            String orderIdStr = root.path("orderId").asText();

            if ("ReserveInventoryCommand".equals(commandType) || root.has("items")) {
                ReserveInventoryCommand cmd = objectMapper.readValue(payload, ReserveInventoryCommand.class);
                boolean outOfStock = cmd.items().stream()
                        .anyMatch(item -> item.productSku() != null && item.productSku().contains("OUT_OF_STOCK"));

                if (outOfStock) {
                    log.warn("Simulated Inventory: Items out of stock for order [{}]", cmd.orderId());
                    InventoryReservationFailedEvent failureEvent = new InventoryReservationFailedEvent(
                            cmd.sagaId(),
                            cmd.orderId(),
                            "Item SKU is out of stock in warehouse"
                    );
                    sendReply("InventoryReservationFailedEvent", cmd.orderId().toString(), failureEvent);
                } else {
                    log.info("Simulated Inventory: Reserved stock for order [{}]", cmd.orderId());
                    InventoryReservedEvent successEvent = new InventoryReservedEvent(cmd.sagaId(), cmd.orderId());
                    sendReply("InventoryReservedEvent", cmd.orderId().toString(), successEvent);
                }
            } else if ("ReleaseInventoryCommand".equals(commandType) || root.has("reason")) {
                ReleaseInventoryCommand cmd = objectMapper.readValue(payload, ReleaseInventoryCommand.class);
                log.info("Simulated Inventory: Released inventory for order [{}] reason: {}", cmd.orderId(), cmd.reason());
                InventoryReleasedEvent releasedEvent = new InventoryReleasedEvent(cmd.sagaId(), cmd.orderId());
                sendReply("InventoryReleasedEvent", cmd.orderId().toString(), releasedEvent);
            }
        } catch (Exception ex) {
            log.error("Simulated Inventory: Error processing command", ex);
        }
    }

    private void sendReply(String eventType, String key, Object reply) throws Exception {
        String json = objectMapper.writeValueAsString(reply);
        org.apache.kafka.clients.producer.ProducerRecord<String, String> record =
                new org.apache.kafka.clients.producer.ProducerRecord<>(KafkaConfig.INVENTORY_REPLIES_TOPIC, key, json);
        record.headers().add(new org.apache.kafka.common.header.internals.RecordHeader("event_type", eventType.getBytes()));
        kafkaTemplate.send(record);
    }
}
