package com.engine.order.infrastructure.adapter.out.outbox;

import com.engine.order.domain.event.DomainEvent;
import com.engine.order.domain.port.out.EventPublisherPort;
import com.engine.order.infrastructure.adapter.out.outbox.entity.OutboxMessageJpaEntity;
import com.engine.order.infrastructure.adapter.out.outbox.repository.OutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Primary
public class OutboxEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisherAdapter.class);

    private final OutboxJpaRepository outboxJpaRepository;
    private final ObjectMapper objectMapper;
    private final com.engine.order.application.port.out.EventStorePort eventStorePort;

    public OutboxEventPublisherAdapter(
            OutboxJpaRepository outboxJpaRepository,
            ObjectMapper objectMapper,
            com.engine.order.application.port.out.EventStorePort eventStorePort
    ) {
        this.outboxJpaRepository = Objects.requireNonNull(outboxJpaRepository, "outboxJpaRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.eventStorePort = Objects.requireNonNull(eventStorePort, "eventStorePort must not be null");
    }

    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "DomainEvent must not be null");
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            OutboxMessageJpaEntity outboxEntity = OutboxMessageJpaEntity.pending(
                    event.eventId(),
                    "Order",
                    event.orderId().value(),
                    event.getClass().getSimpleName(),
                    jsonPayload
            );

            outboxJpaRepository.save(outboxEntity);

            // Append to immutable Event Store with sequential version
            long nextSeq = eventStorePort.getNextSequenceNumber(event.orderId().value());
            eventStorePort.append(
                    event.eventId(),
                    event.orderId().value(),
                    nextSeq,
                    event.getClass().getSimpleName(),
                    jsonPayload,
                    null
            );

            log.info("Transactional Outbox: Persisted event [{}] with ID [{}] (seq {}) for order [{}]",
                    event.getClass().getSimpleName(), event.eventId(), nextSeq, event.orderId().value());
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize domain event: {}", event, ex);
            throw new RuntimeException("Failed to serialize domain event for outbox", ex);
        }
    }
}
