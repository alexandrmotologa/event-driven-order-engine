package com.engine.order.infrastructure.adapter.in.rest;

import com.engine.order.infrastructure.adapter.in.rest.sse.OrderSseNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order Streaming (SSE)", description = "Server-Sent Events streaming endpoints for real-time order lifecycle telemetry")
public class OrderSseRestController {

    private final OrderSseNotificationService sseService;

    public OrderSseRestController(OrderSseNotificationService sseService) {
        this.sseService = Objects.requireNonNull(sseService, "sseService must not be null");
    }

    @GetMapping(path = "/{orderId}/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to live order updates", description = "Establishes a persistent Server-Sent Events (SSE) stream for real-time status transitions of a single order.")
    public SseEmitter streamOrderLive(
            @Parameter(description = "Order UUID to subscribe to") @PathVariable String orderId
    ) {
        return sseService.subscribeToOrder(orderId);
    }

    @GetMapping(path = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to global order events feed", description = "Establishes a persistent Server-Sent Events (SSE) stream for all order events and saga state transitions.")
    public SseEmitter streamAllOrdersLive() {
        return sseService.subscribeToAll();
    }
}
