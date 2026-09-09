package com.engine.order.infrastructure.adapter.in.rest;

import com.engine.order.application.dto.OrderResponseDto;
import com.engine.order.application.service.OrderCommandService;
import com.engine.order.application.service.OrderQueryService;
import com.engine.order.infrastructure.adapter.in.rest.dto.CancelOrderRequest;
import com.engine.order.infrastructure.adapter.in.rest.dto.CreateOrderRequest;
import com.engine.order.infrastructure.adapter.in.rest.dto.ProcessPaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Orders", description = "Order Management and Deterministic Lifecycle Operations")
public class OrderRestController {

    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;

    public OrderRestController(
            OrderCommandService orderCommandService,
            OrderQueryService orderQueryService
    ) {
        this.orderCommandService = orderCommandService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a new Order", description = "Initializes an order in CREATED state with validated items and calculated total.")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, description = "Unique UUID key ensuring idempotent order creation", required = false)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully", content = @Content(schema = @Schema(implementation = OrderResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Concurrent execution conflict", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Domain invariant violation", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<OrderResponseDto> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponseDto response = orderCommandService.handleCreateOrder(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Order by ID", description = "Retrieves current order state and details by unique Order ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order retrieved successfully", content = @Content(schema = @Schema(implementation = OrderResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<OrderResponseDto> getOrder(
            @PathVariable("id") UUID id
    ) {
        OrderResponseDto response = orderQueryService.handleGetOrder(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/{id}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cancel an Order", description = "Transitions order from CREATED/VALIDATED/PAYMENT_PENDING to CANCELLED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully", content = @Content(schema = @Schema(implementation = OrderResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Invalid state transition (e.g. already completed)", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<OrderResponseDto> cancelOrder(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        OrderResponseDto response = orderCommandService.handleCancelOrder(id, request.reason());
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/{id}/pay", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Process Payment for Order", description = "Validates and transitions order to PAID if successful, or CANCELLED if failed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment processed successfully", content = @Content(schema = @Schema(implementation = OrderResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Invalid state transition", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<OrderResponseDto> processPayment(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ProcessPaymentRequest request
    ) {
        OrderResponseDto response = orderCommandService.handleProcessPayment(id, request.toCommand());
        return ResponseEntity.ok(response);
    }
}
