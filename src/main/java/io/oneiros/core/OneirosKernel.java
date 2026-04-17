package io.oneiros.core;

import io.oneiros.core.event.CoreEventBus;
import io.oneiros.core.event.OneirosCoreEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;

/**
 * The Kernel is the central orchestrator of the Oneiros-Core framework.
 * It manages the lifecycle and communication between different Essences.
 */
public final class OneirosKernel {
    private static final Logger log = LoggerFactory.getLogger(OneirosKernel.class);
    
    private final EssenceRegistry registry = new EssenceRegistry();
    private final ServiceRegistry services = new ServiceRegistry();
    private final CoreEventBus eventBus = new CoreEventBus();
    private final java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean(false);

    private OneirosKernel() {}

    /**
     * Builder for clean Kernel initialization.
     */
    public static class Builder {
        private final OneirosKernel kernel = new OneirosKernel();

        /**
         * Adds an Essence to the Kernel.
         */
        public <T extends Essence> Builder addEssence(Class<T> type, T instance) {
            kernel.registry.register(type, instance);
            return this;
        }

        /**
         * Initializes and builds the Kernel.
         */
        public OneirosKernel build() {
            kernel.initializeAll();
            return kernel;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private void initializeAll() {
        log.info("🚀 Oneiros Kernel starting...");
        
        // Use Virtual Threads for parallel essence initialization (Java 21+)
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            registry.getAll().values().forEach(essence -> 
                executor.submit(() -> {
                    try {
                        log.debug("⚙️ Initializing essence: {}", essence.getId());
                        essence.onInitialize(this);
                        // Emit ready event
                        eventBus.publish(new OneirosCoreEvent.EssenceReady(essence.getId(), Instant.now().toEpochMilli()));
                    } catch (Exception e) {
                        log.error("💥 Failed to initialize essence {}: {}", essence.getId(), e.getMessage());
                        if (essence.getPolicy() == Essence.Policy.REQUIRED) {
                            log.error("🛑 CRITICAL: REQUIRED essence '{}' failed! System unstable.", essence.getId());
                            failed.set(true);
                        }
                    }
                })
            );
        }
        
        if (failed.get()) {
            log.error("💀 Kernel initialization failed due to REQUIRED essence failure.");
            shutdown();
            throw new RuntimeException("Kernel boot aborted.");
        }
    }

    /**
     * Registers a service in the global service registry.
     */
    public <T> void registerService(Class<T> type, T implementation) {
        services.registerService(type, implementation);
    }

    /**
     * Retrieves a service by its interface.
     */
    public <T> T service(Class<T> type) {
        return services.getService(type).orElseThrow(() -> 
            new IllegalStateException("Service " + type.getSimpleName() + " not found!"));
    }

    /**
     * Retrieves a registered essence.
     * 
     * @throws IllegalStateException if essence is not found
     */
    public <T extends Essence> T use(Class<T> type) {
        return registry.get(type).orElseThrow(() -> 
            new IllegalStateException("Essence " + type.getSimpleName() + " not found in Kernel!"));
    }

    /**
     * Access the system event bus.
     */
    public CoreEventBus events() {
        return eventBus;
    }

    /**
     * Shuts down all essences.
     */
    public void shutdown() {
        log.info("🛑 Oneiros Kernel shutting down...");
        registry.getAll().values().forEach(Essence::onShutdown);
    }
}
