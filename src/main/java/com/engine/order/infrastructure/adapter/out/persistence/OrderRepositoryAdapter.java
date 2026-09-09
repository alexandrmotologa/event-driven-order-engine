package com.engine.order.infrastructure.adapter.out.persistence;

import com.engine.order.domain.model.Order;
import com.engine.order.domain.model.OrderId;
import com.engine.order.domain.port.out.OrderRepositoryPort;
import com.engine.order.infrastructure.adapter.out.persistence.entity.OrderJpaEntity;
import com.engine.order.infrastructure.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.engine.order.infrastructure.adapter.out.persistence.repository.SpringDataOrderRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataOrderRepository springDataOrderRepository;
    private final OrderPersistenceMapper mapper;

    public OrderRepositoryAdapter(
            SpringDataOrderRepository springDataOrderRepository,
            OrderPersistenceMapper mapper
    ) {
        this.springDataOrderRepository = Objects.requireNonNull(springDataOrderRepository, "springDataOrderRepository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = mapper.toJpaEntity(order);
        OrderJpaEntity saved = springDataOrderRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return springDataOrderRepository.findById(orderId.value())
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsById(OrderId orderId) {
        return springDataOrderRepository.existsById(orderId.value());
    }
}
