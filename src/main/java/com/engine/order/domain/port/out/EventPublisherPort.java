package com.engine.order.domain.port.out;

import com.engine.order.domain.event.DomainEvent;

import java.util.List;

public interface EventPublisherPort {

    void publish(DomainEvent event);

    default void publishAll(List<DomainEvent> events) {
        if (events != null) {
            events.forEach(this::publish);
        }
    }
}
