package com.engine.order.infrastructure.eventsourcing;

import com.engine.order.application.dto.CreateOrderCommand;
import com.engine.order.application.dto.OrderItemCommand;
import com.engine.order.application.dto.OrderResponseDto;
import com.engine.order.application.dto.OrderSnapshotRecord;
import com.engine.order.application.port.in.OrderHistoryUseCase;
import com.engine.order.application.service.OrderCommandService;
import com.engine.order.domain.model.OrderId;
import com.engine.order.domain.model.OrderState;
import com.engine.order.domain.port.out.EventPublisherPort;
import com.engine.order.domain.port.out.OrderRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SnapshotEventSourcingIntegrationTest {

    @Autowired
    private OrderCommandService orderCommandService;

    @Autowired
    private OrderRepositoryPort orderRepositoryPort;

    @Autowired
    private EventPublisherPort eventPublisherPort;

    @Autowired
    private OrderHistoryUseCase orderHistoryUseCase;

    @Test
    @DisplayName("Should create snapshot, list snapshots, and optimize time-travel replay using snapshots")
    void shouldCreateSnapshotAndReplayAccurately() {
        // 1. Create order
        UUID customerId = UUID.randomUUID();
        CreateOrderCommand command = new CreateOrderCommand(
                customerId,
                "USD",
                List.of(new OrderItemCommand("SKU-PRO-01", 2, new BigDecimal("150.00")))
        );

        OrderResponseDto created = orderCommandService.handleCreateOrder(command);
        UUID orderId = created.id();

        // 2. Advance state: Validate -> Initiate Payment -> Mark Paid
        var order = orderRepositoryPort.findById(OrderId.of(orderId)).orElseThrow();
        order = order.validate();
        eventPublisherPort.publishAll(order.getDomainEvents());

        order = order.initiatePayment();
        eventPublisherPort.publishAll(order.getDomainEvents());

        order = order.markPaid("TX-SNAPSHOT-999");
        eventPublisherPort.publishAll(order.getDomainEvents());
        order = orderRepositoryPort.save(order);

        // 3. Take snapshot of state PAID
        orderHistoryUseCase.createSnapshot(orderId);

        // Verify snapshot was stored
        List<OrderSnapshotRecord> snapshots = orderHistoryUseCase.getSnapshots(orderId);
        assertThat(snapshots).hasSize(1);
        OrderSnapshotRecord snapshot = snapshots.get(0);
        assertThat(snapshot.orderId()).isEqualTo(orderId.toString());
        assertThat(snapshot.state()).isEqualTo(OrderState.PAID.name());
        assertThat(snapshot.snapshotVersion()).isGreaterThanOrEqualTo(1L);

        // 4. Advance state further: Allocate Inventory -> Complete
        order = orderRepositoryPort.findById(OrderId.of(orderId)).orElseThrow();
        order = order.allocateInventory();
        eventPublisherPort.publishAll(order.getDomainEvents());

        order = order.complete();
        eventPublisherPort.publishAll(order.getDomainEvents());
        order = orderRepositoryPort.save(order);

        // 5. Replay to the snapshot version: should hydrate directly from snapshot
        OrderResponseDto replayedAtSnapshot = orderHistoryUseCase.replayOrderToVersion(orderId, snapshot.snapshotVersion());
        assertThat(replayedAtSnapshot.id()).isEqualTo(orderId);
        assertThat(replayedAtSnapshot.state()).isEqualTo(OrderState.PAID.name());
        assertThat(replayedAtSnapshot.totalAmount()).isEqualByComparingTo("300.00");

        // 6. Replay to the latest completed version: should use snapshot as starting point and fold remaining
        OrderResponseDto replayedFinal = orderHistoryUseCase.replayOrderToVersion(orderId, Long.MAX_VALUE);
        assertThat(replayedFinal.id()).isEqualTo(orderId);
        assertThat(replayedFinal.state()).isEqualTo(OrderState.COMPLETED.name());
        assertThat(replayedFinal.customerId()).isEqualTo(customerId);
        assertThat(replayedFinal.totalAmount()).isEqualByComparingTo("300.00");
    }
}
