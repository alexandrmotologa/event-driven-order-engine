package com.engine.order.infrastructure.adapter.out.persistence.mapper;

import com.engine.order.domain.model.*;
import com.engine.order.infrastructure.adapter.out.persistence.entity.OrderItemJpaEntity;
import com.engine.order.infrastructure.adapter.out.persistence.entity.OrderJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderPersistenceMapper {

    public OrderJpaEntity toJpaEntity(Order domainOrder) {
        OrderJpaEntity entity = new OrderJpaEntity(
                domainOrder.getId().value(),
                domainOrder.getCustomerId().value(),
                domainOrder.getState(),
                domainOrder.getTotalAmount().currency().getCurrencyCode(),
                domainOrder.getTotalAmount().amount(),
                domainOrder.getVersion(),
                domainOrder.getCancellationReason().orElse(null),
                domainOrder.getCreatedAt(),
                domainOrder.getUpdatedAt()
        );

        for (OrderItem item : domainOrder.getItems()) {
            OrderItemJpaEntity itemEntity = new OrderItemJpaEntity(
                    item.getId(),
                    entity,
                    item.getProductSku(),
                    item.getQuantity(),
                    item.getUnitPrice().amount(),
                    item.getUnitPrice().currency().getCurrencyCode(),
                    item.getSubtotal().amount()
            );
            entity.addItem(itemEntity);
        }

        return entity;
    }

    public Order toDomain(OrderJpaEntity entity) {
        String currencyCode = entity.getCurrency();

        List<OrderItem> items = entity.getItems().stream()
                .map(itemEntity -> new OrderItem(
                        itemEntity.getId(),
                        itemEntity.getProductSku(),
                        itemEntity.getQuantity(),
                        Money.of(itemEntity.getUnitPrice(), itemEntity.getCurrency())
                ))
                .toList();

        return Order.reconstitute(
                com.engine.order.domain.model.OrderId.of(entity.getId()),
                CustomerId.of(entity.getCustomerId()),
                entity.getState(),
                items,
                Money.of(entity.getTotalAmount(), currencyCode),
                entity.getCancellationReason(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
