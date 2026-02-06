package io.oneiros.pool;

import io.oneiros.client.OneirosClient;
import reactor.core.publisher.Mono;


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
    private volatile long createdAt;
    private volatile long totalRequests;
    private volatile long successfulRequests;
    private volatile long failedRequests;
    private volatile long totalResponseTime; // in milliseconds

    public PooledConnection(OneirosClient client) {
        this.client = client;
        this.status = Status.HEALTHY;
        this.lastUsed = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
        this.failureCount = 0;
        this.totalRequests = 0;
        this.successfulRequests = 0;
        this.failedRequests = 0;
        this.totalResponseTime = 0;
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
     * Records a successful request and its response time.
     */
    public void recordSuccess(long responseTimeMs) {
        this.totalRequests++;
        this.successfulRequests++;
        this.totalResponseTime += responseTimeMs;
        this.lastUsed = System.currentTimeMillis();
    }

    /**
     * Records a failed request.
     */
    public void recordFailure() {
        this.totalRequests++;
        this.failedRequests++;
        this.lastUsed = System.currentTimeMillis();
        incrementFailureCount();
    }

    /**
     * Returns connection statistics.
     */
    public ConnectionStats getStats() {
        return new ConnectionStats(
            status,
            createdAt,
            lastUsed,
            totalRequests,
            successfulRequests,
            failedRequests,
            failureCount,
            totalRequests > 0 ? (double) totalResponseTime / totalRequests : 0.0
        );
    }

    /**
     * Connection statistics holder.
     */
    public static class ConnectionStats {
        private final Status status;
        private final long createdAt;
        private final long lastUsed;
        private final long totalRequests;
        private final long successfulRequests;
        private final long failedRequests;
        private final int consecutiveFailures;
        private final double avgResponseTimeMs;

        public ConnectionStats(Status status, long createdAt, long lastUsed,
                              long totalRequests, long successfulRequests,
                              long failedRequests, int consecutiveFailures,
                              double avgResponseTimeMs) {
            this.status = status;
            this.createdAt = createdAt;
            this.lastUsed = lastUsed;
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.failedRequests = failedRequests;
            this.consecutiveFailures = consecutiveFailures;
            this.avgResponseTimeMs = avgResponseTimeMs;
        }

        public Status getStatus() { return status; }
        public long getCreatedAt() { return createdAt; }
        public long getLastUsed() { return lastUsed; }
        public long getTotalRequests() { return totalRequests; }
        public long getSuccessfulRequests() { return successfulRequests; }
        public long getFailedRequests() { return failedRequests; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public double getAvgResponseTimeMs() { return avgResponseTimeMs; }

        public long getUptimeMs() {
            return System.currentTimeMillis() - createdAt;
        }

        public long getIdleTimeMs() {
            return System.currentTimeMillis() - lastUsed;
        }

        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRequests / totalRequests * 100.0 : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "ConnectionStats{status=%s, uptime=%dms, idle=%dms, requests=%d, " +
                "success=%.2f%%, avgResponseTime=%.2fms, consecutiveFailures=%d}",
                status, getUptimeMs(), getIdleTimeMs(), totalRequests,
                getSuccessRate(), avgResponseTimeMs, consecutiveFailures
            );
        }
    }

    /**
     * Performs a health check using the ping RPC method.
     * This is more reliable than a query because it doesn't require SQL parsing.
     */
    public Mono<Boolean> healthCheck() {
        return client.ping()
            .then(Mono.just(true))
            .doOnNext(healthy -> {
                if (healthy) {
                    resetFailureCount();
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
