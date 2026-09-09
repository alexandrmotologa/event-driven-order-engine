package com.engine.order.infrastructure.adapter.in.kafka;

import com.engine.order.infrastructure.adapter.out.persistence.entity.ConsumedMessageJpaEntity;
import com.engine.order.infrastructure.adapter.out.persistence.repository.ConsumedMessageJpaRepository;
import com.engine.order.infrastructure.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class IdempotentOrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdempotentOrderEventConsumer.class);

    private final ConsumedMessageJpaRepository consumedMessageJpaRepository;
    private final List<String> processedMessageIds = Collections.synchronizedList(new ArrayList<>());

    public IdempotentOrderEventConsumer(ConsumedMessageJpaRepository consumedMessageJpaRepository) {
        this.consumedMessageJpaRepository = Objects.requireNonNull(consumedMessageJpaRepository, "consumedMessageJpaRepository must not be null");
    }

    @KafkaListener(
            topics = KafkaConfig.ORDER_EVENTS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id:order-engine-consumer-group}"
    )
    @Transactional
    public void consume(
            @Payload String payload,
            @Header(name = "message_id", required = false) byte[] messageIdBytes,
            @Header(name = "event_type", required = false) byte[] eventTypeBytes
    ) {
        String messageId = messageIdBytes != null ? new String(messageIdBytes) : "UNKNOWN";
        String eventType = eventTypeBytes != null ? new String(eventTypeBytes) : "UNKNOWN";

        log.info("Kafka Consumer received event [{}] with messageId [{}]", eventType, messageId);

        // 1. Idempotency Check: deduplicate via consumed_messages table
        if (!"UNKNOWN".equals(messageId) && consumedMessageJpaRepository.existsById(messageId)) {
            log.warn("Idempotent Consumer: Duplicate message [{}] detected, skipping processing.", messageId);
            return;
        }

        // 2. Business processing logic (e.g. update read models, trigger sagas, etc.)
        handleEvent(eventType, payload);

        // 3. Record consumption atomically in same transaction
        if (!"UNKNOWN".equals(messageId)) {
            consumedMessageJpaRepository.save(new ConsumedMessageJpaEntity(
                    messageId,
                    "IdempotentOrderEventConsumer",
                    Instant.now()
            ));
            processedMessageIds.add(messageId);
            log.info("Idempotent Consumer: Successfully processed and recorded message [{}]", messageId);
        }
    }

    private void handleEvent(String eventType, String payload) {
        log.debug("Processing business logic for event [{}] with payload length: {}", eventType, payload.length());
    }

    public List<String> getProcessedMessageIds() {
        return Collections.unmodifiableList(new ArrayList<>(processedMessageIds));
    }

    public void clear() {
        processedMessageIds.clear();
    }
}
