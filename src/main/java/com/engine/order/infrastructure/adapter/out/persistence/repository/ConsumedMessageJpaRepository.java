package com.engine.order.infrastructure.adapter.out.persistence.repository;

import com.engine.order.infrastructure.adapter.out.persistence.entity.ConsumedMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsumedMessageJpaRepository extends JpaRepository<ConsumedMessageJpaEntity, String> {
}
