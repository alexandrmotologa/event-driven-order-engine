package com.engine.order.infrastructure.metrics;

import com.engine.order.infrastructure.adapter.out.outbox.entity.OutboxStatus;
import com.engine.order.infrastructure.adapter.out.outbox.repository.OutboxJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class OrderMetrics {

    private final MeterRegistry meterRegistry;
    private final OutboxJpaRepository outboxJpaRepository;
    private final Timer outboxPublishTimer;

    public OrderMetrics(MeterRegistry meterRegistry, OutboxJpaRepository outboxJpaRepository) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.outboxJpaRepository = Objects.requireNonNull(outboxJpaRepository, "outboxJpaRepository must not be null");

        this.outboxPublishTimer = Timer.builder("orders.outbox.publishing.latency")
                .description("Latency of transactional outbox relay publishing to Kafka")
                .publishPercentileHistogram()
                .register(meterRegistry);

        Gauge.builder("orders.outbox.pending.count", outboxJpaRepository,
                        repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("Number of pending outbox messages awaiting dispatch to Kafka")
                .register(meterRegistry);
    }

    public void recordStateTransition(String fromState, String toState) {
        Counter.builder("orders.state.transitions.count")
                .description("Count of order domain state transitions")
                .tag("from_state", fromState != null ? fromState : "NONE")
                .tag("to_state", toState != null ? toState : "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }

    public void recordSagaFailure(String sagaName, String reason) {
        Counter.builder("orders.saga.failures.count")
                .description("Count of failed/compensated distributed saga workflows")
                .tag("saga_name", sagaName != null ? sagaName : "UNKNOWN_SAGA")
                .tag("reason", reason != null ? reason : "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }

    public void recordOutboxPublishLatency(long durationMillis) {
        outboxPublishTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public void recordOutboxPublishLatency(Duration duration) {
        outboxPublishTimer.record(duration);
    }
}
