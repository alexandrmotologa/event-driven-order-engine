package com.engine.order.infrastructure.sse;

import com.engine.order.infrastructure.adapter.in.rest.DashboardViewController;
import com.engine.order.infrastructure.adapter.in.rest.OrderSseRestController;
import com.engine.order.infrastructure.adapter.in.rest.sse.OrderSseNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderSseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderSseNotificationService sseNotificationService;

    @Autowired
    private OrderSseRestController sseRestController;

    @Autowired
    private DashboardViewController dashboardViewController;

    @Test
    @DisplayName("Should forward /dashboard and / to /dashboard/index.html")
    void shouldForwardDashboard() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/dashboard/index.html"));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/dashboard/index.html"));
    }

    @Test
    @DisplayName("Should create SSE subscription and broadcast events without error")
    void shouldSubscribeAndBroadcastSse() {
        String orderId = UUID.randomUUID().toString();

        // 1. Subscribe to order
        SseEmitter orderEmitter = sseRestController.streamOrderLive(orderId);
        assertThat(orderEmitter).isNotNull();

        // 2. Subscribe to global
        SseEmitter globalEmitter = sseRestController.streamAllOrdersLive();
        assertThat(globalEmitter).isNotNull();

        // 3. Broadcast event
        sseNotificationService.broadcastOrderEvent(
                orderId,
                "OrderPaidEvent",
                "PAID",
                "IN_PROGRESS",
                "Payment authorized"
        );
    }

    @Test
    @DisplayName("Should verify SSE endpoints accept GET requests with text/event-stream")
    void shouldAcceptSseGetRequests() throws Exception {
        String orderId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/live")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/live")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());
    }
}
