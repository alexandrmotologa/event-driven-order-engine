package com.engine.order.infrastructure.adapter.out.persistence;

import com.engine.order.application.dto.OrderEventStreamRecord;
import com.engine.order.application.port.out.EventStorePort;
import com.engine.order.infrastructure.adapter.out.persistence.entity.OrderEventStreamJpaEntity;
import com.engine.order.infrastructure.adapter.out.persistence.repository.OrderEventStreamJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class EventStoreRepositoryAdapter implements EventStorePort {

    private final OrderEventStreamJpaRepository jpaRepository;

    public EventStoreRepositoryAdapter(OrderEventStreamJpaRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository must not be null");
    }

    @Override
    @Transactional
    public void append(UUID eventId, UUID orderId, long sequenceNumber, String eventType, String payload, String metadata) {
        OrderEventStreamJpaEntity entity = new OrderEventStreamJpaEntity(
                eventId.toString(),
                orderId.toString(),
                sequenceNumber,
                eventType,
                payload,
                metadata,
                Instant.now()
        );
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderEventStreamRecord> getEventsForOrder(UUID orderId) {
        return jpaRepository.findByOrderIdOrderBySequenceNumberAsc(orderId.toString())
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderEventStreamRecord> getEventsForOrderUpTo(UUID orderId, long maxSequenceNumber) {
        return jpaRepository.findByOrderIdAndSequenceNumberLessThanEqualOrderBySequenceNumberAsc(orderId.toString(), maxSequenceNumber)
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getNextSequenceNumber(UUID orderId) {
        return jpaRepository.findMaxSequenceNumberByOrderId(orderId.toString())
                .map(seq -> seq + 1)
                .orElse(1L);
    }

    private OrderEventStreamRecord toRecord(OrderEventStreamJpaEntity entity) {
        return new OrderEventStreamRecord(
                entity.getEventId(),
                entity.getOrderId(),
                entity.getSequenceNumber(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getMetadata(),
                entity.getCreatedAt()
        );
    }
}
