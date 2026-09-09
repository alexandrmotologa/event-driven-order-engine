package com.engine.order.infrastructure.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBucketRateLimiterService {

    private final boolean enabled;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiterService(@Value("${app.ratelimit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean tryAcquire(String tenantId, int tokens) {
        if (!enabled) {
            return true;
        }
        TokenBucket bucket = buckets.computeIfAbsent(tenantId, this::createBucketForTenant);
        return bucket.tryAcquire(tokens);
    }

    public long getRetryAfterSeconds(String tenantId, int tokens) {
        TokenBucket bucket = buckets.computeIfAbsent(tenantId, this::createBucketForTenant);
        return bucket.getRetryAfterSeconds(tokens);
    }

    public void reset() {
        buckets.clear();
    }

    private TokenBucket createBucketForTenant(String tenantId) {
        if (tenantId != null && (tenantId.contains("enterprise") || tenantId.contains("vip"))) {
            return new TokenBucket(100, 20.0); // 100 burst, 20/sec
        }
        return new TokenBucket(10, 2.0); // 10 burst, 2/sec
    }

    private static class TokenBucket {
        private final double capacity;
        private final double refillRate; // tokens per second
        private double availableTokens;
        private long lastRefillTimestamp;

        public TokenBucket(double capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.availableTokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        public synchronized boolean tryAcquire(int tokens) {
            refill();
            if (availableTokens >= tokens) {
                availableTokens -= tokens;
                return true;
            }
            return false;
        }

        public synchronized long getRetryAfterSeconds(int tokens) {
            refill();
            if (availableTokens >= tokens) {
                return 0;
            }
            double deficit = tokens - availableTokens;
            double seconds = deficit / refillRate;
            return Math.max(1, (long) Math.ceil(seconds));
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double deltaSeconds = (now - lastRefillTimestamp) / 1000.0;
            if (deltaSeconds > 0) {
                availableTokens = Math.min(capacity, availableTokens + deltaSeconds * refillRate);
                lastRefillTimestamp = now;
            }
        }
    }
}
