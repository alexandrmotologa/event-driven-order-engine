package com.engine.order.infrastructure.adapter.out.persistence;

import com.engine.order.application.dto.OrderSnapshotRecord;
import com.engine.order.application.port.out.SnapshotPort;
import com.engine.order.infrastructure.adapter.out.persistence.entity.SnapshotJpaEntity;
import com.engine.order.infrastructure.adapter.out.persistence.repository.SnapshotJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class SnapshotRepositoryAdapter implements SnapshotPort {

    private final SnapshotJpaRepository jpaRepository;

    public SnapshotRepositoryAdapter(SnapshotJpaRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository must not be null");
    }

    @Override
    @Transactional
    public void saveSnapshot(UUID orderId, long snapshotVersion, String state, String aggregateState) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(aggregateState, "aggregateState must not be null");

        SnapshotJpaEntity entity = new SnapshotJpaEntity(
                UUID.randomUUID().toString(),
                orderId.toString(),
                snapshotVersion,
                state,
                aggregateState,
                Instant.now()
        );
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderSnapshotRecord> findLatestSnapshotUpTo(UUID orderId, long maxVersion) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        return jpaRepository.findFirstByOrderIdAndSnapshotVersionLessThanEqualOrderBySnapshotVersionDesc(orderId.toString(), maxVersion)
                .map(this::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSnapshotRecord> getSnapshotsForOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        return jpaRepository.findByOrderIdOrderBySnapshotVersionDesc(orderId.toString())
                .stream()
                .map(this::toRecord)
                .toList();
    }

    private OrderSnapshotRecord toRecord(SnapshotJpaEntity entity) {
        return new OrderSnapshotRecord(
                entity.getId(),
                entity.getOrderId(),
                entity.getSnapshotVersion(),
                entity.getState(),
                entity.getAggregateState(),
                entity.getCreatedAt()
        );
    }
}
