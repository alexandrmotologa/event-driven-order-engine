package com.engine.order.infrastructure.adapter.in.rest;

import com.engine.order.application.dto.OrderEventStreamRecord;
import com.engine.order.application.dto.OrderResponseDto;
import com.engine.order.application.port.in.OrderHistoryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Order Event Sourcing & Audit History", description = "Endpoints for immutable event store audit history and time-travel aggregate replay")
public class OrderHistoryQueryRestController {

    private final OrderHistoryUseCase orderHistoryUseCase;

    public OrderHistoryQueryRestController(OrderHistoryUseCase orderHistoryUseCase) {
        this.orderHistoryUseCase = Objects.requireNonNull(orderHistoryUseCase, "orderHistoryUseCase must not be null");
    }

    @GetMapping("/history")
    @Operation(summary = "Get Order Audit Trail", description = "Retrieves the immutable sequence of domain events recorded in the event store for the given order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit trail retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderEventStreamRecord.class))))
    })
    public ResponseEntity<List<OrderEventStreamRecord>> getOrderHistory(
            @Parameter(description = "Order UUID") @PathVariable UUID orderId
    ) {
        List<OrderEventStreamRecord> history = orderHistoryUseCase.getAuditTrail(orderId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/replay")
    @Operation(summary = "Time-Travel Replay Order State", description = "Reconstructs the order aggregate state by replaying events up to the specified target sequence number.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order reconstructed at target version",
                    content = @Content(schema = @Schema(implementation = OrderResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Order or event sequence not found")
    })
    public ResponseEntity<OrderResponseDto> replayOrder(
            @Parameter(description = "Order UUID") @PathVariable UUID orderId,
            @Parameter(description = "Target sequence number to replay up to")
            @RequestParam(name = "targetVersion", required = false) Long targetVersion,
            @Parameter(description = "Alternative parameter name for target version")
            @RequestParam(name = "version", required = false) Long version
    ) {
        long effectiveVersion = targetVersion != null ? targetVersion : (version != null ? version : Long.MAX_VALUE);
        OrderResponseDto replayed = orderHistoryUseCase.replayOrderToVersion(orderId, effectiveVersion);
        return ResponseEntity.ok(replayed);
    }

    @GetMapping("/snapshots")
    @Operation(summary = "List Order Snapshots", description = "Retrieves all periodic aggregate snapshots captured for this order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Snapshots retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = com.engine.order.application.dto.OrderSnapshotRecord.class))))
    })
    public ResponseEntity<List<com.engine.order.application.dto.OrderSnapshotRecord>> getSnapshots(
            @Parameter(description = "Order UUID") @PathVariable UUID orderId
    ) {
        List<com.engine.order.application.dto.OrderSnapshotRecord> snapshots = orderHistoryUseCase.getSnapshots(orderId);
        return ResponseEntity.ok(snapshots);
    }

    @PostMapping("/snapshots")
    @Operation(summary = "Take Order Snapshot", description = "Manually triggers an immediate aggregate snapshot at the current sequence version.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Snapshot triggered successfully"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<Void> takeSnapshot(
            @Parameter(description = "Order UUID") @PathVariable UUID orderId
    ) {
        orderHistoryUseCase.createSnapshot(orderId);
        return ResponseEntity.ok().build();
    }
}

