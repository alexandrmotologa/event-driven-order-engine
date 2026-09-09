package com.engine.order.infrastructure.adapter.out.persistence;

import com.engine.order.application.dto.OrderSummaryDto;
import com.engine.order.application.port.out.OrderSummaryViewRepositoryPort;
import com.engine.order.infrastructure.adapter.out.persistence.entity.OrderSummaryViewJpaEntity;
import com.engine.order.infrastructure.adapter.out.persistence.repository.OrderSummaryViewJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class OrderSummaryViewRepositoryAdapter implements OrderSummaryViewRepositoryPort {

    private final OrderSummaryViewJpaRepository jpaRepository;

    public OrderSummaryViewRepositoryAdapter(OrderSummaryViewJpaRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository must not be null");
    }

    @Override
    @Transactional
    public void save(OrderSummaryDto summary) {
        OrderSummaryViewJpaEntity entity = new OrderSummaryViewJpaEntity(
                summary.orderId(),
                summary.customerId(),
                summary.status(),
                summary.totalAmount(),
                summary.currency(),
                summary.itemCount(),
                summary.createdAt(),
                summary.updatedAt(),
                summary.lastEventType(),
                summary.sagaStatus()
        );
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderSummaryDto> findById(String orderId) {
        return jpaRepository.findById(orderId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryDto> findAll(String customerId, String status) {
        return jpaRepository.findByCriteria(customerId, status)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private OrderSummaryDto toDto(OrderSummaryViewJpaEntity entity) {
        return new OrderSummaryDto(
                entity.getOrderId(),
                entity.getCustomerId(),
                entity.getStatus(),
                entity.getTotalAmount(),
                entity.getCurrency(),
                entity.getItemCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastEventType(),
                entity.getSagaStatus()
        );
    }
}
