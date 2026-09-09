package com.engine.order.infrastructure.adapter.in.kafka.simulator;

import com.engine.order.application.saga.message.AuthorizePaymentCommand;
import com.engine.order.application.saga.message.PaymentAuthorizedEvent;
import com.engine.order.application.saga.message.PaymentFailedEvent;
import com.engine.order.application.saga.message.PaymentRefundedEvent;
import com.engine.order.application.saga.message.RefundPaymentCommand;
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

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Component
public class SimulatedPaymentService {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public SimulatedPaymentService(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @KafkaListener(
            topics = KafkaConfig.PAYMENT_COMMANDS_TOPIC,
            groupId = "simulated-payment-group"
    )
    public void onPaymentCommand(
            @Payload String payload,
            @Header(name = "command_type", required = false) byte[] commandTypeBytes
    ) {
        String commandType = commandTypeBytes != null ? new String(commandTypeBytes) : "UNKNOWN";
        log.info("Simulated Payment Service: Received command [{}]", commandType);

        try {
            JsonNode root = objectMapper.readTree(payload);

            if ("AuthorizePaymentCommand".equals(commandType) || (root.has("amount") && !root.has("reason"))) {
                AuthorizePaymentCommand cmd = objectMapper.readValue(payload, AuthorizePaymentCommand.class);

                // Configurable failure trigger: if currency is "FAIL" or amount is exactly 999.00
                boolean shouldFail = "FAIL".equalsIgnoreCase(cmd.currency())
                        || cmd.amount().compareTo(new BigDecimal("999.00")) == 0;

                if (shouldFail) {
                    log.warn("Simulated Payment: Card declined / Insufficient funds for order [{}]", cmd.orderId());
                    PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                            cmd.sagaId(),
                            cmd.orderId(),
                            "Insufficient funds / Card declined by payment gateway"
                    );
                    sendReply("PaymentFailedEvent", cmd.orderId().toString(), failedEvent);
                } else {
                    String transactionId = "TX-SIM-" + UUID.randomUUID().toString().substring(0, 8);
                    log.info("Simulated Payment: Authorized payment [{}] for order [{}]", transactionId, cmd.orderId());
                    PaymentAuthorizedEvent authorizedEvent = new PaymentAuthorizedEvent(
                            cmd.sagaId(),
                            cmd.orderId(),
                            transactionId
                    );
                    sendReply("PaymentAuthorizedEvent", cmd.orderId().toString(), authorizedEvent);
                }
            } else if ("RefundPaymentCommand".equals(commandType) || root.has("reason")) {
                RefundPaymentCommand cmd = objectMapper.readValue(payload, RefundPaymentCommand.class);
                String refundTxId = "REF-SIM-" + UUID.randomUUID().toString().substring(0, 8);
                log.info("Simulated Payment: Refunded order [{}] with tx [{}]", cmd.orderId(), refundTxId);
                PaymentRefundedEvent refundedEvent = new PaymentRefundedEvent(
                        cmd.sagaId(),
                        cmd.orderId(),
                        refundTxId
                );
                sendReply("PaymentRefundedEvent", cmd.orderId().toString(), refundedEvent);
            }
        } catch (Exception ex) {
            log.error("Simulated Payment: Error processing command", ex);
        }
    }

    private void sendReply(String eventType, String key, Object reply) throws Exception {
        String json = objectMapper.writeValueAsString(reply);
        org.apache.kafka.clients.producer.ProducerRecord<String, String> record =
                new org.apache.kafka.clients.producer.ProducerRecord<>(KafkaConfig.PAYMENT_REPLIES_TOPIC, key, json);
        record.headers().add(new org.apache.kafka.common.header.internals.RecordHeader("event_type", eventType.getBytes()));
        kafkaTemplate.send(record);
    }
}
