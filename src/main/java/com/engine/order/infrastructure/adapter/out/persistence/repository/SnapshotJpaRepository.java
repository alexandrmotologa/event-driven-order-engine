package com.engine.order.infrastructure.adapter.out.persistence.repository;

import com.engine.order.infrastructure.adapter.out.persistence.entity.SnapshotJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnapshotJpaRepository extends JpaRepository<SnapshotJpaEntity, String> {

    List<SnapshotJpaEntity> findByOrderIdOrderBySnapshotVersionDesc(String orderId);

    Optional<SnapshotJpaEntity> findFirstByOrderIdAndSnapshotVersionLessThanEqualOrderBySnapshotVersionDesc(String orderId, Long maxVersion);
}
