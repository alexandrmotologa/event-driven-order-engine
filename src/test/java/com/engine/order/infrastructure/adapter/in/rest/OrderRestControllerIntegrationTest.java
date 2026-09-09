package com.engine.order.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderRestControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/orders creates order successfully and returns 201")
    void shouldCreateOrderSuccessfully() throws Exception {
        String payload = """
                {
                  "customerId": "%s",
                  "currency": "USD",
                  "items": [
                    {
                      "productSku": "PROD-A",
                      "quantity": 2,
                      "unitPrice": 49.99
                    },
                    {
                      "productSku": "PROD-B",
                      "quantity": 1,
                      "unitPrice": 15.00
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.totalAmount").value(114.98))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("Idempotency-Key header returns cached response on replay")
    void shouldReturnCachedResponseForSameIdempotencyKey() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String payload = """
                {
                  "customerId": "%s",
                  "currency": "USD",
                  "items": [
                    {
                      "productSku": "IDEM-PROD-1",
                      "quantity": 1,
                      "unitPrice": 99.00
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        // First call
        MvcResult firstResult = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        String firstResponse = firstResult.getResponse().getContentAsString();
        Map<?, ?> firstOrder = objectMapper.readValue(firstResponse, Map.class);
        String firstOrderId = (String) firstOrder.get("id");

        // Second call with identical key
        MvcResult secondResult = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        String secondResponse = secondResult.getResponse().getContentAsString();
        Map<?, ?> secondOrder = objectMapper.readValue(secondResponse, Map.class);
        String secondOrderId = (String) secondOrder.get("id");

        assertThat(firstOrderId).isEqualTo(secondOrderId);
        assertThat(firstResponse).isEqualTo(secondResponse);
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} returns 200 OK for existing order")
    void shouldGetExistingOrder() throws Exception {
        // Create order
        String payload = """
                {
                  "customerId": "%s",
                  "currency": "EUR",
                  "items": [
                    {
                      "productSku": "GET-SKU",
                      "quantity": 1,
                      "unitPrice": 30.00
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> created = objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        String orderId = (String) created.get("id");

        // Retrieve order
        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(30.00));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} returns 404 RFC 7807 ProblemDetail for non-existing order")
    void shouldReturn404ForNonExistentOrder() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/orders/{id}", randomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.engine.order.com/errors/order-not-found"))
                .andExpect(jsonPath("$.title").value("Order Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail", containsString(randomId.toString())));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel cancels order and updates state")
    void shouldCancelOrderSuccessfully() throws Exception {
        // Create order
        String payload = """
                {
                  "customerId": "%s",
                  "currency": "USD",
                  "items": [
                    {
                      "productSku": "CANCEL-SKU",
                      "quantity": 1,
                      "unitPrice": 50.00
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> created = objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        String orderId = (String) created.get("id");

        // Cancel order
        String cancelPayload = """
                {
                  "reason": "Customer cancelled directly via UI"
                }
                """;

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.state").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Customer cancelled directly via UI"));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/pay processes payment and transitions to PAID")
    void shouldProcessPaymentSuccessfully() throws Exception {
        // Create order
        String payload = """
                {
                  "customerId": "%s",
                  "currency": "USD",
                  "items": [
                    {
                      "productSku": "PAY-SKU",
                      "quantity": 2,
                      "unitPrice": 100.00
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> created = objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        String orderId = (String) created.get("id");

        // Pay order
        String payPayload = """
                {
                  "transactionId": "TX-PAY-88221",
                  "successful": true
                }
                """;

        mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.state").value("PAID"));
    }

    @Test
    @DisplayName("Validation failure returns 400 Bad Request ProblemDetail")
    void shouldReturn400OnValidationFailure() throws Exception {
        String invalidPayload = """
                {
                  "customerId": null,
                  "currency": "INVALID",
                  "items": []
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request Validation Error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    @DisplayName("Invalid state jump returns 409 Conflict ProblemDetail")
    void shouldReturn409OnInvalidStateJump() throws Exception {
        // Create and Cancel order
        String payload = """
                {
                  "customerId": "%s",
                  "currency": "USD",
                  "items": [
                    {
                      "productSku": "CONFLICT-SKU",
                      "quantity": 1,
                      "unitPrice": 10.00
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> created = objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        String orderId = (String) created.get("id");

        // Cancel order
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "First cancel" }
                                """))
                .andExpect(status().isOk());

        // Attempting to cancel an already cancelled order -> InvalidStateTransitionException -> 409 Conflict
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Second cancel attempt" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.engine.order.com/errors/invalid-state-transition"))
                .andExpect(jsonPath("$.title").value("Invalid State Transition"))
                .andExpect(jsonPath("$.status").value(409));
    }
}
