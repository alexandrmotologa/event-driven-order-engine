package com.engine.order.infrastructure.ratelimit;

import com.engine.order.infrastructure.config.KafkaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

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
        "saga.timeout.enabled=false",
        "app.ratelimit.enabled=true"
})
@DirtiesContext
class RateLimitingAndMultiTenancyIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenBucketRateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
    }

    @Test
    @DisplayName("Should throttle requests with 429 when quota exceeded and isolate buckets across tenants")
    void shouldThrottleWhenQuotaExceededAndIsolateTenants() {
        String tenantA = "tenant-standard-alpha";
        String tenantB = "tenant-standard-beta";

        HttpHeaders headersA = new HttpHeaders();
        headersA.set("X-Tenant-Id", tenantA);
        HttpEntity<Void> entityA = new HttpEntity<>(headersA);

        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-Tenant-Id", tenantB);
        HttpEntity<Void> entityB = new HttpEntity<>(headersB);

        // 1. Consume all 10 tokens for tenant A
        int successfulCalls = 0;
        for (int i = 0; i < 10; i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/orders/" + UUID.randomUUID(),
                    HttpMethod.GET,
                    entityA,
                    String.class
            );
            // Even 404 is allowed through by rate limiter filter
            if (response.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) {
                successfulCalls++;
            }
        }
        assertThat(successfulCalls).isEqualTo(10);

        // 2. The 11th request for Tenant A should be throttled (429)
        ResponseEntity<String> throttledResponse = restTemplate.exchange(
                "/api/v1/orders/" + UUID.randomUUID(),
                HttpMethod.GET,
                entityA,
                String.class
        );
        assertThat(throttledResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttledResponse.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(throttledResponse.getBody()).contains("Rate limit quota exceeded for tenant [" + tenantA + "]");

        // 3. Tenant B has its own bucket and should NOT be throttled
        ResponseEntity<String> tenantBResponse = restTemplate.exchange(
                "/api/v1/orders/" + UUID.randomUUID(),
                HttpMethod.GET,
                entityB,
                String.class
        );
        assertThat(tenantBResponse.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(tenantBResponse.getHeaders().getFirst("X-Tenant-Id")).isEqualTo(tenantB);
    }
}
