package com.engine.order.infrastructure.security;

import com.engine.order.domain.port.out.IdempotencyStorePort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryIdempotencyStoreAdapter implements IdempotencyStorePort {

    private final Map<String, Boolean> inFlightLocks = new ConcurrentHashMap<>();
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String idempotencyKey) {
        if (idempotencyKey == null) {
            return false;
        }
        return inFlightLocks.putIfAbsent(idempotencyKey, Boolean.TRUE) == null;
    }

    @Override
    public void saveResult(String idempotencyKey, int statusCode, String responsePayload) {
        if (idempotencyKey != null) {
            cache.put(idempotencyKey, new CachedResponse(statusCode, responsePayload));
            inFlightLocks.remove(idempotencyKey);
        }
    }

    @Override
    public Optional<CachedResponse> getCachedResponse(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(idempotencyKey));
    }

    @Override
    public void unlock(String idempotencyKey) {
        if (idempotencyKey != null) {
            inFlightLocks.remove(idempotencyKey);
        }
    }
}
