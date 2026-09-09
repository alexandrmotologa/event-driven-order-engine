package com.engine.order.infrastructure.security;

import com.engine.order.infrastructure.adapter.in.rest.dto.AuthRequest;
import com.engine.order.infrastructure.adapter.in.rest.dto.AuthResponse;
import com.engine.order.infrastructure.adapter.in.rest.dto.CreateOrderRequest;
import com.engine.order.infrastructure.adapter.in.rest.dto.OrderItemRequest;
import com.engine.order.infrastructure.config.KafkaConfig;
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
        "saga.timeout.enabled=false",
        "app.security.enabled=true"
})
@DirtiesContext
class SecurityRbacIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Public routes should be accessible without authentication")
    void publicRoutesShouldBeAccessibleWithoutAuth() {
        ResponseEntity<String> dashboardResponse = restTemplate.getForEntity("/dashboard", String.class);
        assertThat(dashboardResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> healthResponse = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Secured endpoints should reject unauthenticated requests with 401 Unauthorized")
    void securedEndpointsShouldRejectUnauthenticatedRequests() {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                "USD",
                List.of(new OrderItemRequest("SKU-1", 1, new BigDecimal("99.99")))
        );

        ResponseEntity<String> createOrderResponse = restTemplate.postForEntity(
                "/api/v1/orders",
                request,
                String.class
        );
        assertThat(createOrderResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> summaryResponse = restTemplate.getForEntity(
                "/api/v1/orders/summary",
                String.class
        );
        assertThat(summaryResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> historyResponse = restTemplate.getForEntity(
                "/api/v1/orders/" + UUID.randomUUID() + "/history",
                String.class
        );
        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("ROLE_CUSTOMER should be able to create orders but forbidden from admin routes (403)")
    void customerRoleShouldHaveAccessToOrdersButForbiddenFromAdminRoutes() {
        // 1. Issue customer token
        ResponseEntity<AuthResponse> authResponse = restTemplate.postForEntity(
                "/api/v1/auth/token",
                new AuthRequest("customer-bob", "CUSTOMER"),
                AuthResponse.class
        );
        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authResponse.getBody()).isNotNull();
        String token = authResponse.getBody().token();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // 2. Customer creates order -> Allowed
        CreateOrderRequest createRequest = new CreateOrderRequest(
                UUID.randomUUID(),
                "USD",
                List.of(new OrderItemRequest("SKU-1", 2, new BigDecimal("50.00")))
        );
        HttpEntity<CreateOrderRequest> createEntity = new HttpEntity<>(createRequest, headers);

        ResponseEntity<String> createResponse = restTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                createEntity,
                String.class
        );
        assertThat(createResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // 3. Customer attempts admin action (summary query) -> Forbidden (403)
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> summaryResponse = restTemplate.exchange(
                "/api/v1/orders/summary",
                HttpMethod.GET,
                requestEntity,
                String.class
        );
        assertThat(summaryResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. Customer attempts admin action (audit trail replay) -> Forbidden (403)
        ResponseEntity<String> historyResponse = restTemplate.exchange(
                "/api/v1/orders/" + UUID.randomUUID() + "/history",
                HttpMethod.GET,
                requestEntity,
                String.class
        );
        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ROLE_ADMIN should be granted access to both order operations and administrative queries")
    void adminRoleShouldHaveAccessToAdministrativeQueries() {
        // 1. Issue admin token
        ResponseEntity<AuthResponse> authResponse = restTemplate.postForEntity(
                "/api/v1/auth/token",
                new AuthRequest("admin-alice", "ADMIN"),
                AuthResponse.class
        );
        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authResponse.getBody()).isNotNull();
        String token = authResponse.getBody().token();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 2. Admin queries CQRS summary view -> 200 OK
        ResponseEntity<String> summaryResponse = restTemplate.exchange(
                "/api/v1/orders/summary",
                HttpMethod.GET,
                entity,
                String.class
        );
        assertThat(summaryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Admin queries history for an order -> 200 OK
        ResponseEntity<String> historyResponse = restTemplate.exchange(
                "/api/v1/orders/" + UUID.randomUUID() + "/history",
                HttpMethod.GET,
                entity,
                String.class
        );
        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
