package io.oneiros.pool;

/**
 * Exception thrown when the connection pool is overloaded and cannot accept new requests.
 *
 * <p>This is raised when the number of pending requests reaches the configured
 * {@code maxPendingRequests} threshold, acting as a backpressure mechanism to
 * prevent the pool from being overwhelmed.
 *
 * @since 0.4.5
 */
public class PoolOverloadedException extends RuntimeException {

    private final int maxAllowed;
    private final int current;

    /**
     * Creates a new exception indicating pool overload.
     *
     * @param maxAllowed the configured maximum number of pending requests
     * @param current    the current number of pending requests at the time of rejection
     */
    public PoolOverloadedException(int maxAllowed, int current) {
        super(String.format(
            "Connection pool overloaded: %d/%d pending requests. " +
            "Consider increasing maxPendingRequests or reducing request rate.",
            current, maxAllowed
        ));
        this.maxAllowed = maxAllowed;
        this.current = current;
    }

    /**
     * Returns the configured maximum number of pending requests.
     */
    public int getMaxAllowed() {
        return maxAllowed;
    }

    /**
     * Returns the actual number of pending requests when the exception was thrown.
     */
    public int getCurrent() {
        return current;
    }
}
