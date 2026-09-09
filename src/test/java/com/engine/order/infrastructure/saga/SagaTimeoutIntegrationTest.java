package com.engine.order.infrastructure.saga;

import com.engine.order.domain.model.*;
import com.engine.order.domain.port.out.OrderRepositoryPort;
import com.engine.order.infrastructure.adapter.out.saga.entity.SagaInstanceJpaEntity;
import com.engine.order.infrastructure.adapter.out.saga.entity.SagaStatus;
import com.engine.order.infrastructure.adapter.out.saga.repository.SagaInstanceJpaRepository;
import com.engine.order.infrastructure.config.KafkaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
        "spring.kafka.listener.auto-startup=false",
        "outbox.relay.enabled=false",
        "saga.timeout.enabled=false"
})
@DirtiesContext
class SagaTimeoutIntegrationTest {

    @Autowired
    private OrderFulfillmentSagaManager sagaManager;

    @Autowired
    private OrderRepositoryPort orderRepositoryPort;

    @Autowired
    private SagaInstanceJpaRepository sagaInstanceJpaRepository;

    @Test
    @DisplayName("Should detect stuck saga, trigger emergency compensation, and cancel order on timeout")
    void shouldCompensateAndCancelOrderOnSagaTimeout() {
        // 1. Create and persist an order
        OrderId orderId = OrderId.random();
        CustomerId customerId = CustomerId.random();
        OrderItem item = OrderItem.of("PROD-TIME", 2, Money.of(90.00, "USD"));
        Order order = Order.create(orderId, customerId, List.of(item));
        order = order.validate();
        order = order.initiatePayment();
        orderRepositoryPort.save(order);

        // 2. Create a stuck saga in PAYMENT_AUTHORIZATION_PENDING with an old timestamp (exceeded TTL)
        UUID sagaId = UUID.randomUUID();
        Instant pastTime = Instant.now().minus(Duration.ofMinutes(5));
        SagaInstanceJpaEntity stuckSaga = new SagaInstanceJpaEntity(
                sagaId,
                orderId.value(),
                "OrderFulfillmentSaga",
                "PAYMENT_AUTHORIZATION",
                SagaStatus.PAYMENT_AUTHORIZATION_PENDING,
                null,
                null,
                pastTime,
                pastTime
        );
        sagaInstanceJpaRepository.save(stuckSaga);

        // 3. Trigger timeout recovery with a 30s threshold
        int compensatedCount = sagaManager.handleTimeouts(Duration.ofSeconds(30));

        // 4. Assertions
        assertThat(compensatedCount).isGreaterThanOrEqualTo(1);

        SagaInstanceJpaEntity updatedSaga = sagaInstanceJpaRepository.findById(sagaId).orElseThrow();
        assertThat(updatedSaga.getStatus()).isEqualTo(SagaStatus.TIMED_OUT);
        assertThat(updatedSaga.getErrorReason()).contains("Dead-man switch triggered");

        Order updatedOrder = orderRepositoryPort.findById(orderId).orElseThrow();
        assertThat(updatedOrder.getState()).isEqualTo(OrderState.CANCELLED);
    }
}
