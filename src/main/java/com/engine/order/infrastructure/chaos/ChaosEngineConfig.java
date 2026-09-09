package com.engine.order.infrastructure.chaos;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ChaosEngineConfig {

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicLong latencyMs = new AtomicLong(0);
    private final AtomicInteger paymentFailureRate = new AtomicInteger(0);
    private final AtomicBoolean simulatePaymentOutage = new AtomicBoolean(false);

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    public long getLatencyMs() {
        return latencyMs.get();
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs.set(Math.max(0, latencyMs));
    }

    public int getPaymentFailureRate() {
        return paymentFailureRate.get();
    }

    public void setPaymentFailureRate(int rate) {
        this.paymentFailureRate.set(Math.max(0, Math.min(100, rate)));
    }

    public boolean isSimulatePaymentOutage() {
        return simulatePaymentOutage.get();
    }

    public void setSimulatePaymentOutage(boolean outage) {
        this.simulatePaymentOutage.set(outage);
    }

    public void reset() {
        this.enabled.set(false);
        this.latencyMs.set(0);
        this.paymentFailureRate.set(0);
        this.simulatePaymentOutage.set(false);
    }
}
