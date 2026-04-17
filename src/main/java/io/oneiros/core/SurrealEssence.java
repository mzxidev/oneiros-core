package io.oneiros.core;

import io.oneiros.client.OneirosClient;

/**
 * Essence implementation for SurrealDB.
 */
public class SurrealEssence implements DatabaseEssence {
    
    private final OneirosConfig config;
    private Oneiros oneiros;

    public SurrealEssence(OneirosConfig config) {
        this.config = config;
    }

    @Override
    public String getId() {
        return "db.surreal." + config.getDatabase();
    }

    @Override
    public void onInitialize(OneirosKernel kernel) {
        // Build the actual Oneiros instance (which manages client, pool, etc.)
        this.oneiros = Oneiros.builder()
            .config(config)
            .build();
            
        if (config.isAutoConnect()) {
            oneiros.client().connect().block();
        }
    }

    @Override
    public void onShutdown() {
        if (oneiros != null) {
            oneiros.close();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getSession() {
        return (T) oneiros.client();
    }

    /**
     * Helper to get the full Oneiros wrapper if needed.
     */
    public Oneiros getOneiros() {
        return oneiros;
    }
}
