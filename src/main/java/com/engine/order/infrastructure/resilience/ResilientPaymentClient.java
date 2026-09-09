package com.engine.order.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ResilientPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientPaymentClient.class);

    private final AtomicBoolean simulateServiceOutage = new AtomicBoolean(false);

    public record PaymentResult(boolean success, String transactionId, String message, boolean fallback) {}

    public void setSimulateServiceOutage(boolean outage) {
        this.simulateServiceOutage.set(outage);
    }

    public boolean isSimulatingServiceOutage() {
        return simulateServiceOutage.get();
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentService")
    public PaymentResult authorizePayment(UUID orderId, BigDecimal amount, String currency) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

        if (simulateServiceOutage.get()) {
            log.warn("ResilientPaymentClient: Simulated downstream payment gateway 503 SERVICE UNAVAILABLE for order [{}]", orderId);
            throw new PaymentGatewayUnavailableException("Downstream payment service is temporarily unavailable (503)");
        }

        String transactionId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("ResilientPaymentClient: Successfully authorized payment [{}] for order [{}] amount [{} {}]",
                transactionId, orderId, amount, currency);
        return new PaymentResult(true, transactionId, "Payment authorized successfully", false);
    }

    public PaymentResult paymentFallback(UUID orderId, BigDecimal amount, String currency, Throwable throwable) {
        log.error("ResilientPaymentClient FALLBACK triggered for order [{}] due to: {}", orderId, throwable.getMessage());
        return new PaymentResult(false, null, "Fallback: Payment gateway unreachable. " + throwable.getMessage(), true);
    }

    public static class PaymentGatewayUnavailableException extends RuntimeException {
        public PaymentGatewayUnavailableException(String message) {
            super(message);
        }
    }
}
