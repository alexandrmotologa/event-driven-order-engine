package com.engine.order.infrastructure.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Component
public class SagaTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutScheduler.class);

    private final OrderFulfillmentSagaManager sagaManager;

    @Value("${saga.timeout.enabled:true}")
    private boolean timeoutEnabled;

    @Value("${saga.timeout.ttl-seconds:30}")
    private long ttlSeconds;

    public SagaTimeoutScheduler(OrderFulfillmentSagaManager sagaManager) {
        this.sagaManager = Objects.requireNonNull(sagaManager, "sagaManager must not be null");
    }

    @Scheduled(fixedDelayString = "${saga.timeout.interval-ms:5000}")
    public void processSagaTimeouts() {
        if (!timeoutEnabled) {
            return;
        }

        try {
            int compensatedCount = sagaManager.handleTimeouts(Duration.ofSeconds(ttlSeconds));
            if (compensatedCount > 0) {
                log.warn("SagaTimeoutScheduler: Successfully compensated {} stuck saga(s)", compensatedCount);
            }
        } catch (Exception ex) {
            log.error("SagaTimeoutScheduler: Error during saga timeout scan", ex);
        }
    }
}
