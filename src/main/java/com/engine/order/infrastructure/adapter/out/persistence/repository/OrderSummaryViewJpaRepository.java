package com.engine.order.infrastructure.adapter.out.persistence.repository;

import com.engine.order.infrastructure.adapter.out.persistence.entity.OrderSummaryViewJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderSummaryViewJpaRepository extends JpaRepository<OrderSummaryViewJpaEntity, String> {

    @Query("""
        SELECT o FROM OrderSummaryViewJpaEntity o
        WHERE (:customerId IS NULL OR o.customerId = :customerId)
          AND (:status IS NULL OR o.status = :status)
        ORDER BY o.createdAt DESC
    """)
    List<OrderSummaryViewJpaEntity> findByCriteria(
            @Param("customerId") String customerId,
            @Param("status") String status
    );
}
