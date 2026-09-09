package com.engine.order.infrastructure.adapter.out.outbox;

import com.engine.order.infrastructure.adapter.out.messaging.KafkaProducerAdapter;
import com.engine.order.infrastructure.adapter.out.outbox.entity.OutboxMessageJpaEntity;
import com.engine.order.infrastructure.adapter.out.outbox.entity.OutboxStatus;
import com.engine.order.infrastructure.adapter.out.outbox.repository.OutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaProducerAdapter kafkaProducerAdapter;
    private final com.engine.order.infrastructure.metrics.OrderMetrics orderMetrics;

    @Value("${outbox.relay.batch-size:50}")
    private int batchSize;

    @Value("${outbox.relay.max-retries:5}")
    private int maxRetries;

    @Value("${outbox.relay.enabled:true}")
    private boolean relayEnabled;

    public OutboxRelayScheduler(
            OutboxJpaRepository outboxJpaRepository,
            KafkaProducerAdapter kafkaProducerAdapter,
            com.engine.order.infrastructure.metrics.OrderMetrics orderMetrics
    ) {
        this.outboxJpaRepository = Objects.requireNonNull(outboxJpaRepository, "outboxJpaRepository must not be null");
        this.kafkaProducerAdapter = Objects.requireNonNull(kafkaProducerAdapter, "kafkaProducerAdapter must not be null");
        this.orderMetrics = Objects.requireNonNull(orderMetrics, "orderMetrics must not be null");
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:500}")
    public void processOutboxMessages() {
        if (!relayEnabled) {
            return;
        }

        try {
            processPendingBatch();
        } catch (Exception ex) {
            log.error("Error during outbox relay execution", ex);
        }
    }

    @Transactional
    public int processPendingBatch() {
        List<OutboxMessageJpaEntity> pendingMessages = outboxJpaRepository.findPendingBatchWithLock(
                OutboxStatus.PENDING,
                PageRequest.of(0, batchSize)
        );

        if (pendingMessages.isEmpty()) {
            return 0;
        }

        log.debug("Outbox Relay: Polled {} pending messages to publish", pendingMessages.size());

        for (OutboxMessageJpaEntity message : pendingMessages) {
            dispatchMessage(message);
        }

        return pendingMessages.size();
    }

    private void dispatchMessage(OutboxMessageJpaEntity message) {
        long startTime = System.currentTimeMillis();
        try {
            kafkaProducerAdapter.sendOrderEvent(
                    message.getId(),
                    message.getAggregateType(),
                    message.getAggregateId(),
                    message.getEventType(),
                    message.getPayload()
            ).get(5, TimeUnit.SECONDS);

            long duration = System.currentTimeMillis() - startTime;
            orderMetrics.recordOutboxPublishLatency(duration);

            message.markPublished(Instant.now());
            outboxJpaRepository.save(message);
            log.info("Outbox Relay: Message [{}] successfully dispatched and marked PUBLISHED in {}ms", message.getId(), duration);
        } catch (Exception ex) {
            log.error("Outbox Relay: Failed to publish message [{}]", message.getId(), ex);
            message.incrementRetry(maxRetries);
            outboxJpaRepository.save(message);
        }
    }
}
