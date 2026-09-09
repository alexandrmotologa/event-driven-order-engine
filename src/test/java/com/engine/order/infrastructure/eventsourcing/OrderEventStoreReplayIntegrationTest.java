package com.engine.order.infrastructure.eventsourcing;

import com.engine.order.application.dto.OrderEventStreamRecord;
import com.engine.order.application.dto.OrderResponseDto;
import com.engine.order.application.port.out.EventStorePort;
import com.engine.order.domain.model.*;
import com.engine.order.domain.port.out.EventPublisherPort;
import com.engine.order.domain.port.out.OrderRepositoryPort;
import com.engine.order.infrastructure.config.KafkaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
class OrderEventStoreReplayIntegrationTest {

    @Autowired
    private EventStorePort eventStorePort;

    @Autowired
    private OrderRepositoryPort orderRepositoryPort;

    @Autowired
    private EventPublisherPort eventPublisherPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Should capture domain events into event store and support time-travel state replay")
    void shouldCaptureEventsAndSupportTimeTravelReplay() {
        // 1. Create and progress an order through multiple lifecycle stages
        OrderId orderId = OrderId.random();
        CustomerId customerId = CustomerId.random();
        List<OrderItem> items = List.of(
                OrderItem.of("PROD-LAPTOP", 1, Money.of(new BigDecimal("1200.00"), "USD"))
        );

        Order order = Order.create(orderId, customerId, items);
        eventPublisherPort.publishAll(order.getDomainEvents()); // Sequence 1: OrderCreatedEvent
        Order current = orderRepositoryPort.save(order);

        Order validated = current.validate();
        eventPublisherPort.publishAll(validated.getDomainEvents()); // Sequence 2: OrderValidatedEvent
        current = orderRepositoryPort.save(validated);

        Order pending = current.initiatePayment();
        eventPublisherPort.publishAll(pending.getDomainEvents()); // Sequence 3: OrderPaymentPendingEvent
        current = orderRepositoryPort.save(pending);

        Order paid = current.markPaid("tx-sourcing-999");
        eventPublisherPort.publishAll(paid.getDomainEvents()); // Sequence 4: OrderPaidEvent
        current = orderRepositoryPort.save(paid);

        Order allocated = current.allocateInventory();
        eventPublisherPort.publishAll(allocated.getDomainEvents()); // Sequence 5: OrderInventoryAllocatedEvent
        current = orderRepositoryPort.save(allocated);

        Order completed = current.complete();
        eventPublisherPort.publishAll(completed.getDomainEvents()); // Sequence 6: OrderCompletedEvent
        orderRepositoryPort.save(completed);

        // 2. Verify all 6 events were appended to Event Store with strictly monotonic sequence numbers
        List<OrderEventStreamRecord> rawEvents = eventStorePort.getEventsForOrder(orderId.value());
        assertThat(rawEvents).hasSize(6);
        assertThat(rawEvents.get(0).eventType()).isEqualTo("OrderCreatedEvent");
        assertThat(rawEvents.get(0).sequenceNumber()).isEqualTo(1L);

        assertThat(rawEvents.get(1).eventType()).isEqualTo("OrderValidatedEvent");
        assertThat(rawEvents.get(1).sequenceNumber()).isEqualTo(2L);

        assertThat(rawEvents.get(2).eventType()).isEqualTo("OrderPaymentPendingEvent");
        assertThat(rawEvents.get(2).sequenceNumber()).isEqualTo(3L);

        assertThat(rawEvents.get(3).eventType()).isEqualTo("OrderPaidEvent");
        assertThat(rawEvents.get(3).sequenceNumber()).isEqualTo(4L);

        assertThat(rawEvents.get(4).eventType()).isEqualTo("OrderInventoryAllocatedEvent");
        assertThat(rawEvents.get(4).sequenceNumber()).isEqualTo(5L);

        assertThat(rawEvents.get(5).eventType()).isEqualTo("OrderCompletedEvent");
        assertThat(rawEvents.get(5).sequenceNumber()).isEqualTo(6L);

        // 3. Test REST Audit Trail endpoint GET /api/v1/orders/{orderId}/history
        ResponseEntity<List<OrderEventStreamRecord>> historyResponse = restTemplate.exchange(
                "/api/v1/orders/" + orderId.value() + "/history",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyResponse.getBody()).isNotNull();
        assertThat(historyResponse.getBody()).hasSize(6);

        // 4. Test Time-Travel Replay endpoint GET /api/v1/orders/{orderId}/replay?targetVersion={version}
        
        // Time travel to Version 1 (CREATED)
        ResponseEntity<OrderResponseDto> replayV1 = restTemplate.getForEntity(
                "/api/v1/orders/" + orderId.value() + "/replay?targetVersion=1",
                OrderResponseDto.class
        );
        assertThat(replayV1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayV1.getBody()).isNotNull();
        assertThat(replayV1.getBody().state()).isEqualTo("CREATED");
        assertThat(replayV1.getBody().version()).isEqualTo(1L);

        // Time travel to Version 2 (VALIDATED)
        ResponseEntity<OrderResponseDto> replayV2 = restTemplate.getForEntity(
                "/api/v1/orders/" + orderId.value() + "/replay?targetVersion=2",
                OrderResponseDto.class
        );
        assertThat(replayV2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayV2.getBody()).isNotNull();
        assertThat(replayV2.getBody().state()).isEqualTo("VALIDATED");
        assertThat(replayV2.getBody().version()).isEqualTo(2L);

        // Time travel to Version 4 (PAID)
        ResponseEntity<OrderResponseDto> replayV4 = restTemplate.getForEntity(
                "/api/v1/orders/" + orderId.value() + "/replay?targetVersion=4",
                OrderResponseDto.class
        );
        assertThat(replayV4.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayV4.getBody()).isNotNull();
        assertThat(replayV4.getBody().state()).isEqualTo("PAID");
        assertThat(replayV4.getBody().version()).isEqualTo(4L);

        // Time travel to Version 6 (COMPLETED)
        ResponseEntity<OrderResponseDto> replayV6 = restTemplate.getForEntity(
                "/api/v1/orders/" + orderId.value() + "/replay?targetVersion=6",
                OrderResponseDto.class
        );
        assertThat(replayV6.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayV6.getBody()).isNotNull();
        assertThat(replayV6.getBody().state()).isEqualTo("COMPLETED");
        assertThat(replayV6.getBody().version()).isEqualTo(6L);
        assertThat(replayV6.getBody().totalAmount()).isEqualByComparingTo(new BigDecimal("1200.00"));

        // 5. Test 404 for unknown order replay
        ResponseEntity<String> notFoundResponse = restTemplate.getForEntity(
                "/api/v1/orders/" + UUID.randomUUID() + "/replay?targetVersion=1",
                String.class
        );
        assertThat(notFoundResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
