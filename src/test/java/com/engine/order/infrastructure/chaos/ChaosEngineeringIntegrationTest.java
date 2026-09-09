package com.engine.order.infrastructure.chaos;

import com.engine.order.infrastructure.adapter.in.rest.dto.ChaosConfigRequest;
import com.engine.order.infrastructure.adapter.in.rest.dto.ChaosStatusResponse;
import com.engine.order.infrastructure.config.KafkaConfig;
import com.engine.order.infrastructure.resilience.ResilientPaymentClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
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
class ChaosEngineeringIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ResilientPaymentClient resilientPaymentClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterEach
    void tearDown() {
        restTemplate.postForEntity("/api/v1/chaos/reset", null, ChaosStatusResponse.class);
    }

    @Test
    @DisplayName("Should inject payment outage, trip Circuit Breaker, trigger fallback, and recover on reset")
    void shouldInjectChaosAndTripCircuitBreaker() {
        // 1. Initial health check
        ResponseEntity<ChaosStatusResponse> initialStatus = restTemplate.getForEntity(
                "/api/v1/chaos/status",
                ChaosStatusResponse.class
        );
        assertThat(initialStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initialStatus.getBody()).isNotNull();
        assertThat(initialStatus.getBody().enabled()).isFalse();

        // 2. Configure Chaos Outage
        ChaosConfigRequest chaosRequest = new ChaosConfigRequest(true, 50L, 100, true);
        ResponseEntity<ChaosStatusResponse> configResponse = restTemplate.postForEntity(
                "/api/v1/chaos/configure",
                chaosRequest,
                ChaosStatusResponse.class
        );
        assertThat(configResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(configResponse.getBody()).isNotNull();
        assertThat(configResponse.getBody().enabled()).isTrue();
        assertThat(configResponse.getBody().simulatePaymentOutage()).isTrue();

        // 3. Make calls that fail and trigger fallback
        for (int i = 0; i < 5; i++) {
            ResilientPaymentClient.PaymentResult result = resilientPaymentClient.authorizePayment(
                    UUID.randomUUID(),
                    new BigDecimal("100.00"),
                    "USD"
            );
            assertThat(result.fallback()).isTrue();
            assertThat(result.success()).isFalse();
        }

        // 4. Verify Circuit Breaker tripped to OPEN
        ResponseEntity<ChaosStatusResponse> openStatus = restTemplate.getForEntity(
                "/api/v1/chaos/status",
                ChaosStatusResponse.class
        );
        assertThat(openStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openStatus.getBody()).isNotNull();
        assertThat(openStatus.getBody().circuitBreakerState()).isIn("OPEN", "HALF_OPEN");

        // 5. Reset Chaos
        ResponseEntity<ChaosStatusResponse> resetResponse = restTemplate.postForEntity(
                "/api/v1/chaos/reset",
                null,
                ChaosStatusResponse.class
        );
        assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resetResponse.getBody()).isNotNull();
        assertThat(resetResponse.getBody().enabled()).isFalse();
        assertThat(resetResponse.getBody().circuitBreakerState()).isEqualTo("CLOSED");

        // 6. Verify healthy payment authorization resumes
        ResilientPaymentClient.PaymentResult healthyResult = resilientPaymentClient.authorizePayment(
                UUID.randomUUID(),
                new BigDecimal("50.00"),
                "USD"
        );
        assertThat(healthyResult.success()).isTrue();
        assertThat(healthyResult.fallback()).isFalse();
    }
}
