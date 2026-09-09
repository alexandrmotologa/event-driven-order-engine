package com.engine.order.infrastructure.adapter.out.messaging.protobuf;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

@Component
public class ProtobufEventDeserializer {

    public OrderEventEnvelope deserializeEnvelope(byte[] data) {
        Objects.requireNonNull(data, "binary data must not be null");

        try {
            CodedInputStream cis = CodedInputStream.newInstance(data);

            String eventId = null;
            String orderId = null;
            String eventType = null;
            long sequenceNumber = 0L;
            long timestamp = 0L;
            byte[] payload = new byte[0];

            while (!cis.isAtEnd()) {
                int tag = cis.readTag();
                if (tag == 0) {
                    break;
                }

                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                switch (fieldNumber) {
                    case 1 -> eventId = cis.readString();
                    case 2 -> orderId = cis.readString();
                    case 3 -> eventType = cis.readString();
                    case 4 -> sequenceNumber = cis.readInt64();
                    case 5 -> timestamp = cis.readInt64();
                    case 6 -> payload = cis.readBytes().toByteArray();
                    default -> cis.skipField(tag);
                }
            }

            return new OrderEventEnvelope(
                    eventId,
                    orderId,
                    eventType,
                    sequenceNumber,
                    timestamp,
                    payload
            );
        } catch (IOException ex) {
            throw new RuntimeException("Failed to deserialize Protobuf binary envelope", ex);
        }
    }
}
