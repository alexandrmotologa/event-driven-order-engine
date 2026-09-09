package com.engine.order.infrastructure.adapter.in.rest.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class OrderSseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OrderSseNotificationService.class);

    // Timeout: 30 minutes for persistent browser connections
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final Map<String, List<SseEmitter>> orderEmitters = new ConcurrentHashMap<>();
    private final List<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();

    public record OrderEventNotification(
            String orderId,
            String eventType,
            String status,
            String sagaStatus,
            String message,
            Instant timestamp
    ) {}

    public SseEmitter subscribeToOrder(String orderId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        orderEmitters.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeOrderEmitter(orderId, emitter));
        emitter.onTimeout(() -> removeOrderEmitter(orderId, emitter));
        emitter.onError(e -> removeOrderEmitter(orderId, emitter));

        // Send initial connection ACK
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(new OrderEventNotification(orderId, "CONNECTED", "OPEN", "NONE", "Subscribed to live updates for order " + orderId, Instant.now())));
        } catch (IOException e) {
            removeOrderEmitter(orderId, emitter);
        }

        log.info("SSE: Client subscribed to order [{}]", orderId);
        return emitter;
    }

    public SseEmitter subscribeToAll() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        globalEmitters.add(emitter);

        emitter.onCompletion(() -> globalEmitters.remove(emitter));
        emitter.onTimeout(() -> globalEmitters.remove(emitter));
        emitter.onError(e -> globalEmitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(new OrderEventNotification("GLOBAL", "CONNECTED", "OPEN", "NONE", "Connected to global real-time event stream", Instant.now())));
        } catch (IOException e) {
            globalEmitters.remove(emitter);
        }

        log.info("SSE: Client subscribed to global event stream. Total global clients: {}", globalEmitters.size());
        return emitter;
    }

    public void broadcastOrderEvent(String orderId, String eventType, String status, String sagaStatus, String message) {
        OrderEventNotification notification = new OrderEventNotification(
                orderId,
                eventType,
                status,
                sagaStatus,
                message,
                Instant.now()
        );

        // 1. Broadcast to specific order subscribers
        List<SseEmitter> specific = orderEmitters.get(orderId);
        if (specific != null) {
            for (SseEmitter emitter : specific) {
                try {
                    emitter.send(SseEmitter.event().name(eventType).data(notification));
                } catch (IOException e) {
                    removeOrderEmitter(orderId, emitter);
                }
            }
        }

        // 2. Broadcast to global subscribers
        for (SseEmitter emitter : globalEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(notification));
            } catch (IOException e) {
                globalEmitters.remove(emitter);
            }
        }

        log.debug("SSE: Broadcast event [{}] for order [{}]", eventType, orderId);
    }

    private void removeOrderEmitter(String orderId, SseEmitter emitter) {
        List<SseEmitter> list = orderEmitters.get(orderId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                orderEmitters.remove(orderId);
            }
        }
    }
}
