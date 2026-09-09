package com.engine.order.infrastructure.adapter.out.persistence.repository;

import com.engine.order.infrastructure.adapter.out.persistence.entity.OrderEventStreamJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderEventStreamJpaRepository extends JpaRepository<OrderEventStreamJpaEntity, String> {

    List<OrderEventStreamJpaEntity> findByOrderIdOrderBySequenceNumberAsc(String orderId);

    List<OrderEventStreamJpaEntity> findByOrderIdAndSequenceNumberLessThanEqualOrderBySequenceNumberAsc(
            String orderId,
            long maxSequenceNumber
    );

    @Query("SELECT MAX(e.sequenceNumber) FROM OrderEventStreamJpaEntity e WHERE e.orderId = :orderId")
    Optional<Long> findMaxSequenceNumberByOrderId(@Param("orderId") String orderId);
}
