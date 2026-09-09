package com.engine.order.infrastructure.cqrs;

import com.engine.order.application.dto.OrderSummaryDto;
import com.engine.order.infrastructure.adapter.in.kafka.OrderSummaryProjectionConsumer;
import com.engine.order.infrastructure.adapter.in.rest.OrderSummaryQueryRestController;
import com.engine.order.infrastructure.adapter.out.persistence.entity.OrderSummaryViewJpaEntity;
import com.engine.order.infrastructure.adapter.out.persistence.repository.OrderSummaryViewJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OrderSummaryProjectionIntegrationTest {

    @Autowired
    private OrderSummaryProjectionConsumer projectionConsumer;

    @Autowired
    private OrderSummaryViewJpaRepository summaryRepository;

    @Autowired
    private OrderSummaryQueryRestController restController;

    @BeforeEach
    void setUp() {
        summaryRepository.deleteAll();
    }

    @Test
    @DisplayName("Should asynchronously project OrderCreatedEvent and subsequent OrderCompletedEvent into denormalized read model")
    void shouldProjectOrderEventsIntoSummaryView() {
        UUID orderId = UUID.randomUUID();
        String customerId = "CUST-CQRS-001";

        // 1. Simulate consuming OrderCreatedEvent
        String createdPayload = """
                {
                    "eventId": "%s",
                    "orderId": {"value": "%s"},
                    "customerId": {"value": "%s"},
                    "totalAmount": {"amount": 250.00, "currency": "USD"},
                    "occurredOn": "2026-09-09T20:00:00Z"
                }
                """.formatted(UUID.randomUUID(), orderId, customerId);

        projectionConsumer.consume(createdPayload, "OrderCreatedEvent".getBytes(StandardCharsets.UTF_8));

        // Verify read projection is created
        OrderSummaryViewJpaEntity createdEntity = summaryRepository.findById(orderId.toString()).orElseThrow();
        assertThat(createdEntity.getOrderId()).isEqualTo(orderId.toString());
        assertThat(createdEntity.getCustomerId()).isEqualTo(customerId);
        assertThat(createdEntity.getStatus()).isEqualTo("CREATED");
        assertThat(createdEntity.getTotalAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(createdEntity.getCurrency()).isEqualTo("USD");
        assertThat(createdEntity.getSagaStatus()).isEqualTo("STARTED");

        // 2. Simulate consuming OrderCompletedEvent
        String completedPayload = """
                {
                    "eventId": "%s",
                    "orderId": {"value": "%s"},
                    "occurredOn": "2026-09-09T20:05:00Z"
                }
                """.formatted(UUID.randomUUID(), orderId);

        projectionConsumer.consume(completedPayload, "OrderCompletedEvent".getBytes(StandardCharsets.UTF_8));

        // Verify read projection is updated
        OrderSummaryViewJpaEntity completedEntity = summaryRepository.findById(orderId.toString()).orElseThrow();
        assertThat(completedEntity.getStatus()).isEqualTo("COMPLETED");
        assertThat(completedEntity.getSagaStatus()).isEqualTo("COMPLETED");
        assertThat(completedEntity.getLastEventType()).isEqualTo("OrderCompletedEvent");

        // 3. Verify REST query endpoint GET /api/v1/orders/summary
        ResponseEntity<List<OrderSummaryDto>> listResponse = restController.getOrderSummaries(customerId, null);
        assertThat(listResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody().get(0).status()).isEqualTo("COMPLETED");

        ResponseEntity<OrderSummaryDto> singleResponse = restController.getOrderSummaryById(orderId.toString());
        assertThat(singleResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(singleResponse.getBody().orderId()).isEqualTo(orderId.toString());
    }
}
