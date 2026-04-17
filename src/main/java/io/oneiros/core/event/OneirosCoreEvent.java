package io.oneiros.core.event;

/**
 * Base interface for all system-wide events in the Oneiros-Core ecosystem.
 */
public interface OneirosCoreEvent {
    long getTimestamp();
    
    /**
     * Standard event for when an essence has finished initialization.
     */
    record EssenceReady(String essenceId, long timestamp) implements OneirosCoreEvent {
        @Override public long getTimestamp() { return timestamp; }
    }
}
