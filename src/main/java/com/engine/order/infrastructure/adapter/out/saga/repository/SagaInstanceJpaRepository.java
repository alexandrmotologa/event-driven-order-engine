package com.engine.order.infrastructure.adapter.out.saga.repository;

import com.engine.order.infrastructure.adapter.out.saga.entity.SagaInstanceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceJpaRepository extends JpaRepository<SagaInstanceJpaEntity, UUID> {

    Optional<SagaInstanceJpaEntity> findByOrderId(UUID orderId);
}
