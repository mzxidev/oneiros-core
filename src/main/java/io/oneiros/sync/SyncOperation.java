package io.oneiros.sync;

import java.util.Map;

/**
 * Represents a database operation that was performed while offline and needs to be synchronized.
 */
public record SyncOperation(
    String id,
    Method method,
    String thing,
    Object data,
    Map<String, Object> params,
    long timestamp
) {
    public enum Method {
        CREATE, UPDATE, PATCH, DELETE, RELATE, QUERY
    }
}
