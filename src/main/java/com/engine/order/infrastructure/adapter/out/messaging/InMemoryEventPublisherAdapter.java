package com.engine.order.infrastructure.adapter.out.messaging;

import com.engine.order.domain.event.DomainEvent;
import com.engine.order.domain.port.out.EventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class InMemoryEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventPublisherAdapter.class);

    private final List<DomainEvent> publishedEvents = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publish(DomainEvent event) {
        log.info("Published domain event [{}] for order [{}] occurred at [{}]",
                event.getClass().getSimpleName(), event.orderId().value(), event.occurredOn());
        publishedEvents.add(event);
    }

    public List<DomainEvent> getPublishedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(publishedEvents));
    }

    public void clear() {
        publishedEvents.clear();
    }
}
