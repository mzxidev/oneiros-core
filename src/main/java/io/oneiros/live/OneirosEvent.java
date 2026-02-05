package io.oneiros.live;

import java.time.Instant;

/**
 * Represents a real-time event from a LIVE SELECT query.
 * Contains the action type (CREATE, UPDATE, DELETE) and the affected record.
 *
 * @param <T> The entity type
 */
public class OneirosEvent<T> {

    public enum Action {
        CREATE, UPDATE, DELETE, CLOSE
    }

    private final Action action;
    private final T data;
    private final String liveQueryId;
    private final Instant timestamp;
    private final Throwable error;

    public OneirosEvent(Action action, T data, String liveQueryId) {
        this.action = action;
        this.data = data;
        this.liveQueryId = liveQueryId;
        this.timestamp = Instant.now();
        this.error = null;
    }

    public OneirosEvent(Action action, T data, String liveQueryId, Throwable error) {
        this.action = action;
        this.data = data;
        this.liveQueryId = liveQueryId;
        this.timestamp = Instant.now();
        this.error = error;
    }

    public Action getAction() {
        return action;
    }

    public T getData() {
        return data;
    }

    public String getLiveQueryId() {
        return liveQueryId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public boolean isCreate() {
        return action == Action.CREATE;
    }

    public boolean isUpdate() {
        return action == Action.UPDATE;
    }

    public boolean isDelete() {
        return action == Action.DELETE;
    }

    public boolean isClose() {
        return action == Action.CLOSE;
    }

    public boolean hasError() {
        return error != null;
    }

    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        return "OneirosEvent{" +
                "action=" + action +
                ", data=" + data +
                ", liveQueryId='" + liveQueryId + '\'' +
                ", timestamp=" + timestamp +
                ", hasError=" + hasError() +
                '}';
    }
}
