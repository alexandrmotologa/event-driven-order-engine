package com.engine.order.infrastructure.saga;

import com.engine.order.domain.model.CustomerId;
import com.engine.order.domain.model.Money;
import com.engine.order.domain.model.Order;
import com.engine.order.domain.model.OrderId;
import com.engine.order.domain.model.OrderItem;
import com.engine.order.domain.model.OrderState;
import com.engine.order.domain.port.out.OrderRepositoryPort;
import com.engine.order.infrastructure.adapter.out.saga.entity.SagaInstanceJpaEntity;
import com.engine.order.infrastructure.adapter.out.saga.entity.SagaStatus;
import com.engine.order.infrastructure.adapter.out.saga.repository.SagaInstanceJpaRepository;
import com.engine.order.infrastructure.config.KafkaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaConfig.ORDER_EVENTS_TOPIC,
                KafkaConfig.INVENTORY_COMMANDS_TOPIC,
                KafkaConfig.INVENTORY_REPLIES_TOPIC,
                KafkaConfig.PAYMENT_COMMANDS_TOPIC,
                KafkaConfig.PAYMENT_REPLIES_TOPIC,
                KafkaConfig.ORDER_EVENTS_DLQ_TOPIC
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "outbox.relay.enabled=false"
})
@DirtiesContext
class OrderFulfillmentSagaIntegrationTest {

    @Autowired
    private OrderFulfillmentSagaManager sagaManager;

    @Autowired
    private OrderRepositoryPort orderRepositoryPort;

    @Autowired
    private SagaInstanceJpaRepository sagaInstanceJpaRepository;

    @BeforeEach
    void setUp() {
        sagaInstanceJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Saga Scenario 1 (Happy Path): Order completes successfully through Inventory and Payment")
    void shouldCompleteSagaSuccessfully() {
        // 1. Create and persist initial Order
        OrderId orderId = OrderId.random();
        CustomerId customerId = CustomerId.random();
        OrderItem item = OrderItem.of("SKU-HAPPY-PHONE", 1, Money.of(499.00, "USD"));
        Order order = Order.create(orderId, customerId, List.of(item));
        orderRepositoryPort.save(order);

        // 2. Trigger Saga
        UUID sagaId = sagaManager.startSaga(order);

        // 3. Await completion
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order currentOrder = orderRepositoryPort.findById(orderId).orElseThrow();
            assertThat(currentOrder.getState()).isEqualTo(OrderState.COMPLETED);

            SagaInstanceJpaEntity saga = sagaInstanceJpaRepository.findById(sagaId).orElseThrow();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(saga.getCurrentStep()).isEqualTo("COMPLETED");
        });
    }

    @Test
    @DisplayName("Saga Scenario 2 (Payment Failure): Triggers Compensating Transaction to release inventory and cancel order")
    void shouldCompensateWhenPaymentFails() {
        // 1. Create order with amount $999.00 which triggers payment failure in simulator
        OrderId orderId = OrderId.random();
        CustomerId customerId = CustomerId.random();
        OrderItem item = OrderItem.of("SKU-EXPENSIVE-WATCH", 1, Money.of(999.00, "USD"));
        Order order = Order.create(orderId, customerId, List.of(item));
        orderRepositoryPort.save(order);

        // 2. Trigger Saga
        UUID sagaId = sagaManager.startSaga(order);

        // 3. Await compensation
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order currentOrder = orderRepositoryPort.findById(orderId).orElseThrow();
            assertThat(currentOrder.getState()).isEqualTo(OrderState.CANCELLED);
            assertThat(currentOrder.getCancellationReason()).isPresent();
            assertThat(currentOrder.getCancellationReason().get()).contains("payment authorization failure");

            SagaInstanceJpaEntity saga = sagaInstanceJpaRepository.findById(sagaId).orElseThrow();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
            assertThat(saga.getErrorReason()).contains("Insufficient funds");
        });
    }

    @Test
    @DisplayName("Saga Scenario 3 (Inventory Failure): Out of stock item cancels order immediately without payment")
    void shouldCancelWhenInventoryOutOfStock() {
        // 1. Create order with OUT_OF_STOCK SKU
        OrderId orderId = OrderId.random();
        CustomerId customerId = CustomerId.random();
        OrderItem item = OrderItem.of("SKU-OUT_OF_STOCK-ITEM", 1, Money.of(100.00, "USD"));
        Order order = Order.create(orderId, customerId, List.of(item));
        orderRepositoryPort.save(order);

        // 2. Trigger Saga
        UUID sagaId = sagaManager.startSaga(order);

        // 3. Await cancellation
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order currentOrder = orderRepositoryPort.findById(orderId).orElseThrow();
            assertThat(currentOrder.getState()).isEqualTo(OrderState.CANCELLED);
            assertThat(currentOrder.getCancellationReason()).isPresent();
            assertThat(currentOrder.getCancellationReason().get()).contains("Out of stock");

            SagaInstanceJpaEntity saga = sagaInstanceJpaRepository.findById(sagaId).orElseThrow();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
            assertThat(saga.getErrorReason()).contains("out of stock");
        });
    }
}
