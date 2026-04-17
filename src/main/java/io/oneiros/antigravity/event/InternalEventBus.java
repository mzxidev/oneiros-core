package io.oneiros.antigravity.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Reactive event bus for Antigravity system events.
 */
public class InternalEventBus {
    private static final Logger log = LoggerFactory.getLogger(InternalEventBus.class);

    private static class Holder {
        static final InternalEventBus INSTANCE = new InternalEventBus();
    }

    private final Sinks.Many<OneirosEvent> bus;

    private InternalEventBus() {
        // Multicast sink with backpressure buffer
        this.bus = Sinks.many().multicast().onBackpressureBuffer();
        log.info("🚀 Antigravity InternalEventBus initialized");
    }

    public static InternalEventBus getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Publishes an event to the bus.
     */
    public void publish(OneirosEvent event) {
        log.trace("📢 Event: {}", event);
        bus.tryEmitNext(event);
    }

    /**
     * Returns a flux of events filtered by type.
     */
    public <T extends OneirosEvent> Flux<T> listen(Class<T> eventType) {
        return bus.asFlux()
                .ofType(eventType);
    }

    /**
     * Returns a flux of all events.
     */
    public Flux<OneirosEvent> all() {
        return bus.asFlux();
    }
}
