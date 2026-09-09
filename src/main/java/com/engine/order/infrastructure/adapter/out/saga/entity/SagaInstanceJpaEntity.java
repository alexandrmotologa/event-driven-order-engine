package com.engine.order.infrastructure.adapter.out.saga.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_instances")
public class SagaInstanceJpaEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "saga_name", nullable = false, length = 64)
    private String sagaName;

    @Column(name = "current_step", nullable = false, length = 64)
    private String currentStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    private SagaStatus status;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "error_reason")
    private String errorReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SagaInstanceJpaEntity() {
    }

    public SagaInstanceJpaEntity(
            UUID id,
            UUID orderId,
            String sagaName,
            String currentStep,
            SagaStatus status,
            String payload,
            String errorReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.orderId = orderId;
        this.sagaName = sagaName;
        this.currentStep = currentStep;
        this.status = status;
        this.payload = payload;
        this.errorReason = errorReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static SagaInstanceJpaEntity start(UUID sagaId, UUID orderId, String sagaName, String initialStep, String payload) {
        Instant now = Instant.now();
        return new SagaInstanceJpaEntity(
                sagaId,
                orderId,
                sagaName,
                initialStep,
                SagaStatus.STARTED,
                payload,
                null,
                now,
                now
        );
    }

    public void transitionTo(String nextStep, SagaStatus nextStatus) {
        this.currentStep = nextStep;
        this.status = nextStatus;
        this.updatedAt = Instant.now();
    }

    public void markCompensated(String reason) {
        this.status = SagaStatus.COMPENSATED;
        this.errorReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markTimedOut(String reason) {
        this.status = SagaStatus.TIMED_OUT;
        this.errorReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.currentStep = "COMPLETED";
        this.status = SagaStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getSagaName() {
        return sagaName;
    }

    public void setSagaName(String sagaName) {
        this.sagaName = sagaName;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public SagaStatus getStatus() {
        return status;
    }

    public void setStatus(SagaStatus status) {
        this.status = status;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
