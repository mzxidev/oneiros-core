package io.oneiros.antigravity;

import io.oneiros.antigravity.event.InternalEventBus;
import io.oneiros.antigravity.event.OneirosEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for self-healing subagents in the Antigravity ecosystem.
 */
public abstract class Subagent {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final String agentId = UUID.randomUUID().toString().substring(0, 8);
    protected final InternalEventBus eventBus = InternalEventBus.getInstance();
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Disposable eventSubscription;

    /**
     * Starts the agent and subscribes to system events.
     */
    public final void start() {
        if (running.compareAndSet(false, true)) {
            log.info("🤖 Agent {} starting...", agentId);
            
            this.eventSubscription = eventBus.all()
                .subscribe(this::handleSystemEvent);
                
            onStart();
        }
    }

    /**
     * Stops the agent and cleans up resources.
     */
    public final void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("🛑 Agent {} stopping...", agentId);
            if (eventSubscription != null) {
                eventSubscription.dispose();
            }
            onStop();
        }
    }

    private void handleSystemEvent(OneirosEvent event) {
        switch (event) {
            case OneirosEvent.Connected connected -> onConnected(connected);
            case OneirosEvent.Disconnected disconnected -> onDisconnected(disconnected);
            case OneirosEvent.TransactionFailed txFailed -> onTransactionError(txFailed);
            case OneirosEvent.HealthUpdate health -> onHealthUpdate(health);
        }
    }

    // --- Lifecycle Hooks for Subclasses ---

    protected void onStart() {}
    protected void onStop() {}
    
    /**
     * Called when the underlying SurrealDB connection is established.
     * Implementation should trigger recovery or resume pending tasks.
     */
    protected abstract void onConnected(OneirosEvent.Connected event);

    /**
     * Called when the connection is lost.
     * Implementation should switch to local buffering or enter "standby" mode.
     */
    protected abstract void onDisconnected(OneirosEvent.Disconnected event);

    /**
     * Called when a transaction fails.
     * Implementation can implement self-healing (e.g., retry with backoff).
     */
    protected void onTransactionError(OneirosEvent.TransactionFailed event) {
        log.warn("⚠️ Agent {} detected transaction failure: {}", agentId, event.error());
    }

    protected void onHealthUpdate(OneirosEvent.HealthUpdate event) {}

    public boolean isRunning() {
        return running.get();
    }
}
