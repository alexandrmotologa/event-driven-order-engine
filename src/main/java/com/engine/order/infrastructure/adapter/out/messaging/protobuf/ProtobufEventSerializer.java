package com.engine.order.infrastructure.adapter.out.messaging.protobuf;

import com.engine.order.domain.event.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

@Component
public class ProtobufEventSerializer {

    public byte[] serialize(DomainEvent event, long sequenceNumber) {
        Objects.requireNonNull(event, "DomainEvent must not be null");

        try {
            byte[] specificPayload = switch (event) {
                case OrderCreatedEvent e -> serializeOrderCreated(e, sequenceNumber);
                case OrderValidatedEvent e -> serializeSimpleEvent(e.eventId().toString(), e.orderId().value().toString(), sequenceNumber, e.occurredOn().toEpochMilli());
                case OrderPaymentPendingEvent e -> serializeSimpleEvent(e.eventId().toString(), e.orderId().value().toString(), sequenceNumber, e.occurredOn().toEpochMilli());
                case OrderPaidEvent e -> serializeOrderPaid(e, sequenceNumber);
                case OrderInventoryAllocatedEvent e -> serializeSimpleEvent(e.eventId().toString(), e.orderId().value().toString(), sequenceNumber, e.occurredOn().toEpochMilli());
                case OrderCompletedEvent e -> serializeSimpleEvent(e.eventId().toString(), e.orderId().value().toString(), sequenceNumber, e.occurredOn().toEpochMilli());
                case OrderCancelledEvent e -> serializeOrderCancelled(e, sequenceNumber);
                case OrderRefundedEvent e -> serializeSimpleEvent(e.eventId().toString(), e.orderId().value().toString(), sequenceNumber, e.occurredOn().toEpochMilli());
                default -> throw new IllegalArgumentException("Unsupported event type for Protobuf serialization: " + event.getClass().getName());
            };

            return wrapInEnvelope(
                    event.eventId().toString(),
                    event.orderId().value().toString(),
                    event.getClass().getSimpleName(),
                    sequenceNumber,
                    event.occurredOn().toEpochMilli(),
                    specificPayload
            );
        } catch (IOException ex) {
            throw new RuntimeException("Failed to serialize domain event to Protobuf binary format", ex);
        }
    }

    private byte[] wrapInEnvelope(String eventId, String orderId, String eventType, long sequenceNumber, long timestamp, byte[] payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CodedOutputStream cos = CodedOutputStream.newInstance(baos);

        cos.writeString(1, eventId);
        cos.writeString(2, orderId);
        cos.writeString(3, eventType);
        cos.writeInt64(4, sequenceNumber);
        cos.writeInt64(5, timestamp);
        if (payload != null && payload.length > 0) {
            cos.writeBytes(6, ByteString.copyFrom(payload));
        }

        cos.flush();
        return baos.toByteArray();
    }

    private byte[] serializeOrderCreated(OrderCreatedEvent e, long sequenceNumber) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CodedOutputStream cos = CodedOutputStream.newInstance(baos);

        cos.writeString(1, e.eventId().toString());
        cos.writeString(2, e.orderId().value().toString());
        cos.writeString(3, e.customerId().value().toString());
        cos.writeString(4, e.totalAmount().currency().getCurrencyCode());
        cos.writeString(5, e.totalAmount().amount().toPlainString());
        cos.writeInt64(6, e.occurredOn().toEpochMilli());
        cos.writeInt64(7, sequenceNumber);

        cos.flush();
        return baos.toByteArray();
    }

    private byte[] serializeOrderPaid(OrderPaidEvent e, long sequenceNumber) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CodedOutputStream cos = CodedOutputStream.newInstance(baos);

        cos.writeString(1, e.eventId().toString());
        cos.writeString(2, e.orderId().value().toString());
        cos.writeString(3, e.transactionId() != null ? e.transactionId() : "");
        cos.writeInt64(4, e.occurredOn().toEpochMilli());
        cos.writeInt64(5, sequenceNumber);

        cos.flush();
        return baos.toByteArray();
    }

    private byte[] serializeOrderCancelled(OrderCancelledEvent e, long sequenceNumber) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CodedOutputStream cos = CodedOutputStream.newInstance(baos);

        cos.writeString(1, e.eventId().toString());
        cos.writeString(2, e.orderId().value().toString());
        cos.writeString(3, e.reason() != null ? e.reason() : "");
        cos.writeInt64(4, e.occurredOn().toEpochMilli());
        cos.writeInt64(5, sequenceNumber);

        cos.flush();
        return baos.toByteArray();
    }

    private byte[] serializeSimpleEvent(String eventId, String orderId, long sequenceNumber, long timestamp) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CodedOutputStream cos = CodedOutputStream.newInstance(baos);

        cos.writeString(1, eventId);
        cos.writeString(2, orderId);
        cos.writeInt64(3, timestamp);
        cos.writeInt64(4, sequenceNumber);

        cos.flush();
        return baos.toByteArray();
    }
}
