package com.engine.order.infrastructure.adapter.out.saga.repository;

import com.engine.order.infrastructure.adapter.out.saga.entity.SagaInstanceJpaEntity;
import com.engine.order.infrastructure.adapter.out.saga.entity.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceJpaRepository extends JpaRepository<SagaInstanceJpaEntity, UUID> {

    Optional<SagaInstanceJpaEntity> findByOrderId(UUID orderId);

    @Query("""
        SELECT s FROM SagaInstanceJpaEntity s
        WHERE s.status IN :statuses
          AND s.updatedAt < :cutoffTime
        ORDER BY s.updatedAt ASC
    """)
    List<SagaInstanceJpaEntity> findStuckSagas(
            @Param("statuses") Collection<SagaStatus> statuses,
            @Param("cutoffTime") Instant cutoffTime
    );
}
