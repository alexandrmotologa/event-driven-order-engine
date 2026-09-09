package com.engine.order.infrastructure.adapter.out.outbox.repository;

import com.engine.order.infrastructure.adapter.out.outbox.entity.OutboxMessageJpaEntity;
import com.engine.order.infrastructure.adapter.out.outbox.entity.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxJpaRepository extends JpaRepository<OutboxMessageJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT o FROM OutboxMessageJpaEntity o WHERE o.status = :status ORDER BY o.createdAt ASC")
    List<OutboxMessageJpaEntity> findPendingBatchWithLock(
            @Param("status") OutboxStatus status,
            Pageable pageable
    );

    List<OutboxMessageJpaEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);
}
