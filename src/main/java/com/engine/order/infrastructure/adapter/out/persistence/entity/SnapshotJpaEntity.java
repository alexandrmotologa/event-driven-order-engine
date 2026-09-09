package com.engine.order.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "order_snapshots")
public class SnapshotJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "order_id", length = 36, nullable = false)
    private String orderId;

    @Column(name = "snapshot_version", nullable = false)
    private Long snapshotVersion;

    @Column(name = "state", length = 50, nullable = false)
    private String state;

    @Column(name = "aggregate_state", nullable = false, columnDefinition = "TEXT")
    private String aggregateState;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SnapshotJpaEntity() {
    }

    public SnapshotJpaEntity(String id, String orderId, Long snapshotVersion, String state, String aggregateState, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.snapshotVersion = Objects.requireNonNull(snapshotVersion, "snapshotVersion must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.aggregateState = Objects.requireNonNull(aggregateState, "aggregateState must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public Long getSnapshotVersion() {
        return snapshotVersion;
    }

    public String getState() {
        return state;
    }

    public String getAggregateState() {
        return aggregateState;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
