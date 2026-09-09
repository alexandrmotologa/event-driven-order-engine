package com.engine.order.application.port.out;

import com.engine.order.application.dto.OrderSnapshotRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SnapshotPort {

    void saveSnapshot(UUID orderId, long snapshotVersion, String state, String aggregateState);

    Optional<OrderSnapshotRecord> findLatestSnapshotUpTo(UUID orderId, long maxVersion);

    List<OrderSnapshotRecord> getSnapshotsForOrder(UUID orderId);
}
