package com.engine.order.infrastructure.adapter.out.messaging;

import com.engine.order.application.port.out.DlqRedriveDispatcherPort;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class DlqRedriveKafkaAdapter implements DlqRedriveDispatcherPort {

    private static final Logger log = LoggerFactory.getLogger(DlqRedriveKafkaAdapter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public DlqRedriveKafkaAdapter(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
    }

    @Override
    public void dispatch(String topic, String key, String payload) {
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        record.headers().add(new RecordHeader("redrive_id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("redriven_at", Instant.now().toString().getBytes(StandardCharsets.UTF_8)));

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("DLQ Redrive: Successfully published message to topic [{}] with key [{}]", topic, key);
            } else {
                log.error("DLQ Redrive: Failed to republish message to topic [{}] with key [{}]", topic, key, ex);
            }
        });
    }
}
