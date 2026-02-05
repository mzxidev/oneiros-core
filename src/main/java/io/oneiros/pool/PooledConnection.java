package io.oneiros.pool;

import io.oneiros.client.OneirosClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Wrapper around an OneirosClient that tracks its health status.
 * Used by the connection pool to identify and remove unhealthy connections.
 */
public class PooledConnection {

    public enum Status {
        HEALTHY,
        UNHEALTHY,
        RECONNECTING
    }

    private final OneirosClient client;
    private volatile Status status;
    private volatile long lastUsed;
    private volatile int failureCount;

    public PooledConnection(OneirosClient client) {
        this.client = client;
        this.status = Status.HEALTHY;
        this.lastUsed = System.currentTimeMillis();
        this.failureCount = 0;
    }

    public OneirosClient getClient() {
        this.lastUsed = System.currentTimeMillis();
        return client;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void incrementFailureCount() {
        this.failureCount++;
        if (failureCount >= 3) {
            setStatus(Status.UNHEALTHY);
        }
    }

    public void resetFailureCount() {
        this.failureCount = 0;
        setStatus(Status.HEALTHY);
    }

    public boolean isHealthy() {
        return status == Status.HEALTHY;
    }

    /**
     * Performs a health check by executing a simple query.
     */
    public Mono<Boolean> healthCheck() {
        return client.query("SELECT 1", Map.class)
            .hasElements()
            .doOnNext(healthy -> {
                if (healthy) {
                    resetFailureCount();
                } else {
                    incrementFailureCount();
                }
            })
            .onErrorResume(error -> {
                incrementFailureCount();
                return Mono.just(false);
            });
    }

    /**
     * Attempts to reconnect the client.
     */
    public Mono<Void> reconnect() {
        setStatus(Status.RECONNECTING);
        return client.connect()
            .doOnSuccess(v -> resetFailureCount())
            .doOnError(error -> incrementFailureCount());
    }

    /**
     * Closes the connection.
     */
    public Mono<Void> close() {
        setStatus(Status.UNHEALTHY);
        return client.disconnect();
    }
}
