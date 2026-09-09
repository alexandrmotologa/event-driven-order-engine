package com.engine.order.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PaymentResilienceIntegrationTest {

    @Autowired
    private ResilientPaymentClient paymentClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetClient() {
        paymentClient.setSimulateServiceOutage(false);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentService");
        cb.reset();
    }

    @Test
    @DisplayName("Should successfully authorize payment when downstream service is healthy")
    void shouldAuthorizePaymentWhenHealthy() {
        UUID orderId = UUID.randomUUID();
        ResilientPaymentClient.PaymentResult result = paymentClient.authorizePayment(
                orderId,
                new BigDecimal("100.00"),
                "USD"
        );

        assertThat(result.success()).isTrue();
        assertThat(result.fallback()).isFalse();
        assertThat(result.transactionId()).isNotBlank().startsWith("TX-");
    }

    @Test
    @DisplayName("Should trigger fallback and register failures when downstream service encounters outage")
    void shouldTriggerFallbackWhenServiceOutage() {
        paymentClient.setSimulateServiceOutage(true);
        UUID orderId = UUID.randomUUID();

        ResilientPaymentClient.PaymentResult result = paymentClient.authorizePayment(
                orderId,
                new BigDecimal("150.00"),
                "USD"
        );

        assertThat(result.success()).isFalse();
        assertThat(result.fallback()).isTrue();
        assertThat(result.message()).contains("Fallback: Payment gateway unreachable");

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentService");
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isGreaterThan(0);
    }
}
