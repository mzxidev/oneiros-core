package io.oneiros.core;

/**
 * Base interface for all modules (Essences) in the Oneiros-Core ecosystem.
 */
public interface Essence {
    
    enum Policy {
        REQUIRED, // Kernel fails if this essence fails to boot
        OPTIONAL  // Kernel only logs error if this essence fails
    }

    /**
     * Unique identifier for this essence.
     */
    String getId();

    /**
     * Defines if this essence is critical for the system.
     */
    default Policy getPolicy() {
        return Policy.REQUIRED;
    }
    
    /**
     * Called when the essence is registered and the kernel is starting.
     */
    default void onInitialize(OneirosKernel kernel) {}
    
    /**
     * Called when the kernel is shutting down.
     */
    default void onShutdown() {}
}
