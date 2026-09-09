package com.engine.order.application.service;

import com.engine.order.application.dto.CreateOrderCommand;
import com.engine.order.application.dto.OrderResponseDto;
import com.engine.order.application.dto.PaymentResultCommand;
import com.engine.order.domain.exception.OrderNotFoundException;
import com.engine.order.domain.model.*;
import com.engine.order.domain.port.in.CancelOrderUseCase;
import com.engine.order.domain.port.in.CreateOrderUseCase;
import com.engine.order.domain.port.in.ProcessPaymentUseCase;
import com.engine.order.domain.port.out.EventPublisherPort;
import com.engine.order.domain.port.out.OrderRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class OrderCommandService implements CreateOrderUseCase, ProcessPaymentUseCase, CancelOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final EventPublisherPort eventPublisherPort;

    public OrderCommandService(
            OrderRepositoryPort orderRepositoryPort,
            EventPublisherPort eventPublisherPort
    ) {
        this.orderRepositoryPort = Objects.requireNonNull(orderRepositoryPort, "orderRepositoryPort must not be null");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
    }

    public OrderResponseDto handleCreateOrder(CreateOrderCommand command) {
        CustomerId customerId = CustomerId.of(command.customerId());
        List<OrderItem> items = command.items().stream()
                .map(itemCmd -> OrderItem.of(
                        itemCmd.productSku(),
                        itemCmd.quantity(),
                        Money.of(itemCmd.unitPrice(), command.currency())
                ))
                .toList();

        Order order = createOrder(customerId, items);
        return OrderResponseDto.fromDomain(order);
    }

    @Override
    public Order createOrder(CustomerId customerId, List<OrderItem> items) {
        OrderId orderId = OrderId.random();
        Order order = Order.create(orderId, customerId, items);

        Order saved = orderRepositoryPort.save(order);
        eventPublisherPort.publishAll(order.getDomainEvents());
        return saved;
    }

    public OrderResponseDto handleProcessPayment(UUID orderIdValue, PaymentResultCommand command) {
        OrderId orderId = OrderId.of(orderIdValue);
        Order updatedOrder = processPayment(orderId, command.transactionId(), command.successful());
        return OrderResponseDto.fromDomain(updatedOrder);
    }

    @Override
    public Order processPayment(OrderId orderId, String transactionId, boolean successful) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        Order updated;
        if (successful) {
            // Validating first if in CREATED state, then moving to PAYMENT_PENDING and PAID
            if (order.getState() == OrderState.CREATED) {
                order = order.validate();
            }
            if (order.getState() == OrderState.VALIDATED) {
                order = order.initiatePayment();
            }
            updated = order.markPaid(transactionId);
        } else {
            updated = order.cancel("Payment failed for transaction: " + transactionId);
        }

        Order saved = orderRepositoryPort.save(updated);
        eventPublisherPort.publishAll(updated.getDomainEvents());
        return saved;
    }

    public OrderResponseDto handleCancelOrder(UUID orderIdValue, String reason) {
        OrderId orderId = OrderId.of(orderIdValue);
        Order updatedOrder = cancelOrder(orderId, reason);
        return OrderResponseDto.fromDomain(updatedOrder);
    }

    @Override
    public Order cancelOrder(OrderId orderId, String reason) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        Order cancelled = order.cancel(reason);
        Order saved = orderRepositoryPort.save(cancelled);
        eventPublisherPort.publishAll(cancelled.getDomainEvents());
        return saved;
    }
}
