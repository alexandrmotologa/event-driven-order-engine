package com.engine.order.infrastructure.adapter.out.outbox;

import com.engine.order.application.dto.CreateOrderCommand;
import com.engine.order.application.dto.OrderItemCommand;
import com.engine.order.application.dto.OrderResponseDto;
import com.engine.order.application.service.OrderCommandService;
import com.engine.order.infrastructure.adapter.in.kafka.IdempotentOrderEventConsumer;
import com.engine.order.infrastructure.adapter.out.outbox.entity.OutboxMessageJpaEntity;
import com.engine.order.infrastructure.adapter.out.outbox.entity.OutboxStatus;
import com.engine.order.infrastructure.adapter.out.outbox.repository.OutboxJpaRepository;
import com.engine.order.infrastructure.adapter.out.persistence.repository.ConsumedMessageJpaRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaConfig.ORDER_EVENTS_TOPIC}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "outbox.relay.enabled=false"
})
@DirtiesContext
class OutboxAndKafkaIntegrationTest {

    @Autowired
    private OrderCommandService orderCommandService;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private ConsumedMessageJpaRepository consumedMessageJpaRepository;

    @Autowired
    private OutboxRelayScheduler outboxRelayScheduler;

    @Autowired
    private IdempotentOrderEventConsumer idempotentOrderEventConsumer;

    @BeforeEach
    void setUp() {
        outboxJpaRepository.deleteAll();
        consumedMessageJpaRepository.deleteAll();
        idempotentOrderEventConsumer.clear();
    }

    @Test
    @DisplayName("Creating an order writes to Outbox, Relay publishes to Kafka, and Consumer deduplicates")
    void shouldExecuteFullOutboxRelayAndConsumerFlow() {
        // 1. Create an Order
        UUID customerId = UUID.randomUUID();
        CreateOrderCommand command = new CreateOrderCommand(
                customerId,
                "USD",
                List.of(new OrderItemCommand("KAFKA-LAPTOP", 1, new BigDecimal("1500.00")))
        );

        OrderResponseDto response = orderCommandService.handleCreateOrder(command);
        UUID orderId = response.id();

        // 2. Verify Outbox message was persisted in PENDING state (atomic with order)
        List<OutboxMessageJpaEntity> outboxMessages = outboxJpaRepository.findAll();
        assertThat(outboxMessages).hasSize(1);

        OutboxMessageJpaEntity pendingMessage = outboxMessages.getFirst();
        assertThat(pendingMessage.getAggregateId()).isEqualTo(orderId);
        assertThat(pendingMessage.getEventType()).isEqualTo("OrderCreatedEvent");
        assertThat(pendingMessage.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(pendingMessage.getProcessedAt()).isNull();

        // 3. Trigger Outbox Relay Scheduler to dispatch pending messages to Kafka
        int processedCount = outboxRelayScheduler.processPendingBatch();
        assertThat(processedCount).isEqualTo(1);

        // 4. Verify Outbox message transitioned to PUBLISHED
        OutboxMessageJpaEntity publishedMessage = outboxJpaRepository.findById(pendingMessage.getId()).orElseThrow();
        assertThat(publishedMessage.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publishedMessage.getProcessedAt()).isNotNull();

        // 5. Verify Idempotent Consumer received message from Kafka topic
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(idempotentOrderEventConsumer.getProcessedMessageIds())
                    .contains(pendingMessage.getId().toString());
            assertThat(consumedMessageJpaRepository.existsById(pendingMessage.getId().toString())).isTrue();
        });

        // 6. Test Idempotent Consumer Deduplication: Re-deliver identical message
        idempotentOrderEventConsumer.consume(
                publishedMessage.getPayload(),
                publishedMessage.getId().toString().getBytes(),
                "OrderCreatedEvent".getBytes()
        );

        // Verify count of processed messages remained 1 (no duplicate processing)
        assertThat(idempotentOrderEventConsumer.getProcessedMessageIds()).hasSize(1);
    }
}
