package io.oneiros.client.rpc;

import java.util.Map;

/**
 * Modern Java 21+ sealed interface for SurrealDB RPC results.
 */
public sealed interface SurrealResult<T> {
    
    /**
     * Successful RPC response.
     */
    record Success<T>(String id, T result) implements SurrealResult<T> {}
    
    /**
     * Failed RPC response.
     */
    record Failure<T>(String id, Object error) implements SurrealResult<T> {}
    
    /**
     * Real-time live query notification.
     */
    record LiveNotification(String liveQueryId, Map<String, Object> data) implements SurrealResult<Object> {}
}
