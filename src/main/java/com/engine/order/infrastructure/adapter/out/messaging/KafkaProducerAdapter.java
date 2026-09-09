package com.engine.order.infrastructure.adapter.out.messaging;

import com.engine.order.infrastructure.config.KafkaConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class KafkaProducerAdapter {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerAdapter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerAdapter(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
    }

    public CompletableFuture<SendResult<String, String>> sendOrderEvent(
            UUID messageId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload
    ) {
        String key = aggregateId.toString();

        ProducerRecord<String, String> record = new ProducerRecord<>(
                KafkaConfig.ORDER_EVENTS_TOPIC,
                key,
                payload
        );

        record.headers().add(new RecordHeader("message_id", messageId.toString().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("aggregate_type", aggregateType.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("event_type", eventType.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("timestamp", Instant.now().toString().getBytes(StandardCharsets.UTF_8)));

        return kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Kafka Producer: Published event [{}] with key [{}] to partition [{}] offset [{}]",
                                eventType, key, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    } else {
                        log.error("Kafka Producer: Failed to publish event [{}] with key [{}]",
                                eventType, key, ex);
                    }
                });
    }
}
