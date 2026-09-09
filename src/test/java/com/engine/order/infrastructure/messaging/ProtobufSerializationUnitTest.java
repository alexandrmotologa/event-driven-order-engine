package com.engine.order.infrastructure.messaging;

import com.engine.order.domain.event.OrderCancelledEvent;
import com.engine.order.domain.event.OrderCreatedEvent;
import com.engine.order.domain.event.OrderPaidEvent;
import com.engine.order.domain.model.CustomerId;
import com.engine.order.domain.model.Money;
import com.engine.order.domain.model.OrderId;
import com.engine.order.infrastructure.adapter.out.messaging.protobuf.OrderEventEnvelope;
import com.engine.order.infrastructure.adapter.out.messaging.protobuf.ProtobufEventDeserializer;
import com.engine.order.infrastructure.adapter.out.messaging.protobuf.ProtobufEventSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProtobufSerializationUnitTest {

    private ProtobufEventSerializer serializer;
    private ProtobufEventDeserializer deserializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        serializer = new ProtobufEventSerializer();
        deserializer = new ProtobufEventDeserializer();
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @Test
    @DisplayName("Should serialize and deserialize OrderCreatedEvent using compact Protocol Buffers binary format")
    void shouldSerializeAndDeserializeOrderCreatedEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        OrderId orderId = OrderId.random();
        CustomerId customerId = CustomerId.random();
        Money totalAmount = Money.of(new BigDecimal("299.99"), "USD");
        Instant now = Instant.now();
        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId,
                orderId,
                customerId,
                totalAmount,
                now
        );

        long sequenceNumber = 42L;

        // Serialize to Protobuf binary
        byte[] protoBytes = serializer.serialize(event, sequenceNumber);
        assertThat(protoBytes).isNotEmpty();

        // Deserialize from binary
        OrderEventEnvelope envelope = deserializer.deserializeEnvelope(protoBytes);

        assertThat(envelope.eventId()).isEqualTo(eventId.toString());
        assertThat(envelope.orderId()).isEqualTo(orderId.value().toString());
        assertThat(envelope.eventType()).isEqualTo("OrderCreatedEvent");
        assertThat(envelope.sequenceNumber()).isEqualTo(sequenceNumber);
        assertThat(envelope.timestamp()).isEqualTo(now.toEpochMilli());
        assertThat(envelope.payload()).isNotEmpty();

        // Compare payload size: Protobuf binary vs JSON
        byte[] jsonBytes = objectMapper.writeValueAsBytes(event);
        assertThat(protoBytes.length).isLessThan(jsonBytes.length);
        double savings = (1.0 - ((double) protoBytes.length / jsonBytes.length)) * 100.0;
        System.out.printf("JSON Size: %d bytes | Protobuf Size: %d bytes | Bandwidth Savings: %.2f%%%n",
                jsonBytes.length, protoBytes.length, savings);
    }

    @Test
    @DisplayName("Should serialize and deserialize OrderPaidEvent and OrderCancelledEvent")
    void shouldSerializeOtherEvents() {
        OrderId orderId = OrderId.random();

        // OrderPaidEvent
        OrderPaidEvent paidEvent = new OrderPaidEvent(
                UUID.randomUUID(),
                orderId,
                "TX-STRIPE-998877",
                Instant.now()
        );
        byte[] paidBytes = serializer.serialize(paidEvent, 5L);
        OrderEventEnvelope paidEnvelope = deserializer.deserializeEnvelope(paidBytes);
        assertThat(paidEnvelope.eventType()).isEqualTo("OrderPaidEvent");
        assertThat(paidEnvelope.sequenceNumber()).isEqualTo(5L);

        // OrderCancelledEvent
        OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(
                UUID.randomUUID(),
                orderId,
                "Inventory allocation out-of-stock",
                Instant.now()
        );
        byte[] cancelledBytes = serializer.serialize(cancelledEvent, 6L);
        OrderEventEnvelope cancelledEnvelope = deserializer.deserializeEnvelope(cancelledBytes);
        assertThat(cancelledEnvelope.eventType()).isEqualTo("OrderCancelledEvent");
        assertThat(cancelledEnvelope.sequenceNumber()).isEqualTo(6L);
    }
}
