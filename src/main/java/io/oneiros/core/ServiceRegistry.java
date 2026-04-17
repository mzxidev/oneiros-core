package io.oneiros.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for Business Services provided by Essences.
 * Decouples service consumers from specific essence implementations.
 */
public final class ServiceRegistry {
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void registerService(Class<T> serviceInterface, T implementation) {
        services.put(serviceInterface, implementation);
    }

    public <T> Optional<T> getService(Class<T> serviceInterface) {
        return Optional.ofNullable(serviceInterface.cast(services.get(serviceInterface)));
    }
}
