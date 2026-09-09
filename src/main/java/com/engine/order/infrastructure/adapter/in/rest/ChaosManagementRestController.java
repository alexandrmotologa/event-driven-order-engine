package com.engine.order.infrastructure.adapter.in.rest;

import com.engine.order.infrastructure.adapter.in.rest.dto.ChaosConfigRequest;
import com.engine.order.infrastructure.adapter.in.rest.dto.ChaosStatusResponse;
import com.engine.order.infrastructure.chaos.ChaosEngineConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping(path = "/api/v1/chaos", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Chaos Engineering & Fault Injection", description = "Endpoints for injecting network latency, service outages, and testing circuit breaker resilience")
public class ChaosManagementRestController {

    private final ChaosEngineConfig chaosEngineConfig;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ChaosManagementRestController(
            ChaosEngineConfig chaosEngineConfig,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.chaosEngineConfig = Objects.requireNonNull(chaosEngineConfig, "chaosEngineConfig must not be null");
        this.circuitBreakerRegistry = Objects.requireNonNull(circuitBreakerRegistry, "circuitBreakerRegistry must not be null");
    }

    @PostMapping("/configure")
    @Operation(summary = "Configure Chaos Fault Parameters", description = "Applies chaos engineering settings: artificial latency, payment failure rate, or simulated outage.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chaos settings updated",
                    content = @Content(schema = @Schema(implementation = ChaosStatusResponse.class)))
    })
    public ResponseEntity<ChaosStatusResponse> configureChaos(@RequestBody ChaosConfigRequest request) {
        if (request.enabled() != null) {
            chaosEngineConfig.setEnabled(request.enabled());
        }
        if (request.latencyMs() != null) {
            chaosEngineConfig.setLatencyMs(request.latencyMs());
        }
        if (request.paymentFailureRate() != null) {
            chaosEngineConfig.setPaymentFailureRate(request.paymentFailureRate());
        }
        if (request.simulatePaymentOutage() != null) {
            chaosEngineConfig.setSimulatePaymentOutage(request.simulatePaymentOutage());
        }

        return ResponseEntity.ok(getCurrentStatus());
    }

    @GetMapping("/status")
    @Operation(summary = "Get Chaos Status", description = "Retrieves active chaos parameters and current resilience4j CircuitBreaker state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status retrieved",
                    content = @Content(schema = @Schema(implementation = ChaosStatusResponse.class)))
    })
    public ResponseEntity<ChaosStatusResponse> getChaosStatus() {
        return ResponseEntity.ok(getCurrentStatus());
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset Chaos Faults", description = "Disables all fault injection and resets the payment CircuitBreaker to CLOSED state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chaos reset successfully",
                    content = @Content(schema = @Schema(implementation = ChaosStatusResponse.class)))
    })
    public ResponseEntity<ChaosStatusResponse> resetChaos() {
        chaosEngineConfig.reset();
        circuitBreakerRegistry.find("paymentService").ifPresent(CircuitBreaker::reset);
        return ResponseEntity.ok(getCurrentStatus());
    }

    private ChaosStatusResponse getCurrentStatus() {
        String cbState = circuitBreakerRegistry.find("paymentService")
                .map(cb -> cb.getState().name())
                .orElse("UNKNOWN");

        return new ChaosStatusResponse(
                chaosEngineConfig.isEnabled(),
                chaosEngineConfig.getLatencyMs(),
                chaosEngineConfig.getPaymentFailureRate(),
                chaosEngineConfig.isSimulatePaymentOutage(),
                cbState
        );
    }
}
