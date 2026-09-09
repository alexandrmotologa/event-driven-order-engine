package com.engine.order.infrastructure.adapter.in.kafka;

import com.engine.order.application.port.out.DlqPort;
import com.engine.order.infrastructure.config.KafkaConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

@Component
public class OrderEventsDlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsDlqConsumer.class);

    private final DlqPort dlqPort;

    public OrderEventsDlqConsumer(DlqPort dlqPort) {
        this.dlqPort = Objects.requireNonNull(dlqPort, "dlqPort must not be null");
    }

    @KafkaListener(
            topics = KafkaConfig.ORDER_EVENTS_DLQ_TOPIC,
            groupId = "order-dlq-management-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDlqMessage(
            ConsumerRecord<String, String> record,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(name = KafkaHeaders.OFFSET, required = false) Long offset
    ) {
        String messageId = UUID.randomUUID().toString();
        String originalTopic = KafkaConfig.ORDER_EVENTS_TOPIC;
        String exceptionClass = "UnknownException";
        String errorMessage = "Message routed to DLQ";

        if (record.headers().lastHeader("kafka_dlt-original-topic") != null) {
            originalTopic = new String(record.headers().lastHeader("kafka_dlt-original-topic").value(), StandardCharsets.UTF_8);
        }
        if (record.headers().lastHeader("kafka_dlt-exception-fqcn") != null) {
            exceptionClass = new String(record.headers().lastHeader("kafka_dlt-exception-fqcn").value(), StandardCharsets.UTF_8);
        }
        if (record.headers().lastHeader("kafka_dlt-exception-message") != null) {
            errorMessage = new String(record.headers().lastHeader("kafka_dlt-exception-message").value(), StandardCharsets.UTF_8);
        }

        int partitionNum = partition != null ? partition : record.partition();
        long offsetNum = offset != null ? offset : record.offset();

        log.warn("DLQ Consumer: Captured failed message from topic [{}] offset [{}] into DLQ store",
                originalTopic, offsetNum);

        dlqPort.captureMessage(
                messageId,
                originalTopic,
                partitionNum,
                offsetNum,
                record.key(),
                record.value(),
                exceptionClass,
                errorMessage
        );
    }
}
