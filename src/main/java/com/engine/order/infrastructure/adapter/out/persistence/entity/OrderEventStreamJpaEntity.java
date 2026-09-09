package com.engine.order.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "order_event_stream",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_event_stream_sequence", columnNames = {"order_id", "sequence_number"})
        }
)
public class OrderEventStreamJpaEntity {

    @Id
    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "order_id", length = 36, nullable = false)
    private String orderId;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderEventStreamJpaEntity() {
    }

    public OrderEventStreamJpaEntity(
            String eventId,
            String orderId,
            long sequenceNumber,
            String eventType,
            String payload,
            String metadata,
            Instant createdAt
    ) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.sequenceNumber = sequenceNumber;
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.metadata = metadata;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public String getEventId() {
        return eventId;
    }

    public String getOrderId() {
        return orderId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
