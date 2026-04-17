package io.oneiros.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Reactive Event Bus for cross-essence communication.
 */
public final class CoreEventBus {
    private static final Logger log = LoggerFactory.getLogger(CoreEventBus.class);
    
    private final Sinks.Many<OneirosCoreEvent> sink = Sinks.many()
        .multicast()
        .onBackpressureBuffer(10000);

    /**
     * Publishes an event to all subscribers.
     */
    public void publish(OneirosCoreEvent event) {
        log.trace("📢 Core Event: {}", event.getClass().getSimpleName());
        sink.tryEmitNext(event);
    }

    /**
     * Listens to events of a specific type.
     */
    public <T extends OneirosCoreEvent> Flux<T> listen(Class<T> eventType) {
        return sink.asFlux().ofType(eventType);
    }
}
