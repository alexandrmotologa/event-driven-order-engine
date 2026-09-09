package com.engine.order.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityActuatorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderMetrics orderMetrics;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("Should expose Kubernetes liveness and readiness health probes")
    void shouldExposeHealthProbes() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Should record and register custom Prometheus business metrics")
    void shouldRecordCustomMetrics() {
        // Record state transition
        orderMetrics.recordStateTransition("CREATED", "VALIDATED");
        double transitionCount = meterRegistry.get("orders.state.transitions.count")
                .tag("from_state", "CREATED")
                .tag("to_state", "VALIDATED")
                .counter()
                .count();
        assertThat(transitionCount).isGreaterThanOrEqualTo(1.0);

        // Record saga failure
        orderMetrics.recordSagaFailure("OrderFulfillmentSaga", "Payment declined");
        double sagaFailureCount = meterRegistry.get("orders.saga.failures.count")
                .tag("saga_name", "OrderFulfillmentSaga")
                .counter()
                .count();
        assertThat(sagaFailureCount).isGreaterThanOrEqualTo(1.0);

        // Record outbox latency
        orderMetrics.recordOutboxPublishLatency(42);
        long publishCount = meterRegistry.get("orders.outbox.publishing.latency")
                .timer()
                .count();
        assertThat(publishCount).isGreaterThanOrEqualTo(1);

        // Verify pending outbox gauge is registered
        assertThat(meterRegistry.find("orders.outbox.pending.count").gauge()).isNotNull();
    }
}
