package io.oneiros.core;

import io.oneiros.core.event.OneirosCoreEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

/**
 * Enterprise base class for background workers.
 * Handles automatic startup based on system events and graceful shutdown.
 */
public abstract class BackgroundWorkerEssence implements Essence {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private Disposable taskDisposable;

    @Override
    public final void onInitialize(OneirosKernel kernel) {
        // Default: Wait for the specific essence this worker depends on
        // Or just start when the first database is ready
        kernel.events().listen(OneirosCoreEvent.EssenceReady.class)
            .filter(this::shouldStartOn)
            .firstWithSignal()
            .subscribe(event -> {
                log.info("🚀 Background Worker '{}' starting (triggered by {})", getId(), event.essenceId());
                this.taskDisposable = startTask(kernel);
            });
    }

    /**
     * Override to define when the worker should start.
     * Default: Starts as soon as any essence is ready.
     */
    protected boolean shouldStartOn(OneirosCoreEvent.EssenceReady event) {
        return true;
    }

    /**
     * Implementation of the actual background logic.
     * Return a Disposable (e.g. from Flux.interval) for automatic cleanup.
     */
    protected abstract Disposable startTask(OneirosKernel kernel);

    @Override
    public void onShutdown() {
        if (taskDisposable != null && !taskDisposable.isDisposed()) {
            log.info("🛑 Stopping background worker: {}", getId());
            taskDisposable.dispose();
        }
    }
}
