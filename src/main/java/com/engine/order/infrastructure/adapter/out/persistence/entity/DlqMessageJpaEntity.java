package com.engine.order.infrastructure.adapter.out.persistence.entity;

import com.engine.order.domain.model.DlqStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "dlq_messages")
public class DlqMessageJpaEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "original_topic", length = 128, nullable = false)
    private String originalTopic;

    @Column(name = "partition_num", nullable = false)
    private int partitionNum;

    @Column(name = "offset_num", nullable = false)
    private long offsetNum;

    @Column(name = "message_key", length = 128)
    private String messageKey;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "exception_class", length = 256)
    private String exceptionClass;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private DlqStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DlqMessageJpaEntity() {
    }

    public DlqMessageJpaEntity(
            String id,
            String originalTopic,
            int partitionNum,
            long offsetNum,
            String messageKey,
            String payload,
            String exceptionClass,
            String errorMessage,
            DlqStatus status,
            int retryCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.originalTopic = Objects.requireNonNull(originalTopic, "originalTopic must not be null");
        this.partitionNum = partitionNum;
        this.offsetNum = offsetNum;
        this.messageKey = messageKey;
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.exceptionClass = exceptionClass;
        this.errorMessage = errorMessage;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.retryCount = retryCount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public String getId() {
        return id;
    }

    public String getOriginalTopic() {
        return originalTopic;
    }

    public int getPartitionNum() {
        return partitionNum;
    }

    public long getOffsetNum() {
        return offsetNum;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
        this.updatedAt = Instant.now();
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public DlqStatus getStatus() {
        return status;
    }

    public void setStatus(DlqStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
