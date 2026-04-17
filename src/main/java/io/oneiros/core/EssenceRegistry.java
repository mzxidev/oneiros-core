package io.oneiros.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Type-safe registry for Oneiros Essences.
 */
public final class EssenceRegistry {
    private final Map<Class<? extends Essence>, Essence> instances = new ConcurrentHashMap<>();

    /**
     * Registers a new essence instance.
     */
    public <T extends Essence> void register(Class<T> type, T instance) {
        instances.put(type, instance);
    }

    /**
     * Retrieves an essence by its type.
     */
    public <T extends Essence> Optional<T> get(Class<T> type) {
        return Optional.ofNullable(type.cast(instances.get(type)));
    }

    /**
     * Returns all registered instances.
     */
    public Map<Class<? extends Essence>, Essence> getAll() {
        return instances;
    }
}
