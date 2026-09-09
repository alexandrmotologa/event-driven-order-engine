package com.engine.order.infrastructure.adapter.in.rest;

import com.engine.order.application.dto.OrderSummaryDto;
import com.engine.order.application.port.in.OrderSummaryQueryPort;
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

@RestController
@RequestMapping(path = "/api/v1/orders/summary", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Order Summaries (CQRS)", description = "High-performance denormalized query endpoints for order read projections")
public class OrderSummaryQueryRestController {

    private final OrderSummaryQueryPort queryPort;

    public OrderSummaryQueryRestController(OrderSummaryQueryPort queryPort) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
    }

    @GetMapping
    @Operation(summary = "Query Order Summaries", description = "Retrieves denormalized order summaries filtered optionally by customer ID and order status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summaries retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderSummaryDto.class))))
    })
    public ResponseEntity<List<OrderSummaryDto>> getOrderSummaries(
            @Parameter(description = "Optional filter by Customer ID") @RequestParam(required = false) String customerId,
            @Parameter(description = "Optional filter by Order Status") @RequestParam(required = false) String status
    ) {
        List<OrderSummaryDto> summaries = queryPort.getOrderSummaries(customerId, status);
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get Order Summary by ID", description = "Retrieves a single denormalized order summary by order ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary found",
                    content = @Content(schema = @Schema(implementation = OrderSummaryDto.class))),
            @ApiResponse(responseCode = "404", description = "Order summary not found")
    })
    public ResponseEntity<OrderSummaryDto> getOrderSummaryById(
            @Parameter(description = "Order UUID") @PathVariable String orderId
    ) {
        return queryPort.getOrderSummaryById(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
