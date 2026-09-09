package com.engine.order.domain.port.out;

import java.util.Optional;

public interface IdempotencyStorePort {

    boolean tryLock(String idempotencyKey);

    void saveResult(String idempotencyKey, int statusCode, String responsePayload);

    Optional<CachedResponse> getCachedResponse(String idempotencyKey);

    void unlock(String idempotencyKey);

    record CachedResponse(int statusCode, String responsePayload) {}
}
