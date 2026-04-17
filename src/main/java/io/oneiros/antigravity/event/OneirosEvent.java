package io.oneiros.antigravity.event;

/**
 * System-wide events for Antigravity subagents.
 */
public sealed interface OneirosEvent {
    
    /**
     * WebSocket connection established.
     */
    record Connected(String clientId) implements OneirosEvent {}
    
    /**
     * WebSocket connection lost.
     */
    record Disconnected(String clientId, Throwable cause) implements OneirosEvent {}
    
    /**
     * A SurrealDB transaction failed.
     */
    record TransactionFailed(String clientId, String transactionId, String error) implements OneirosEvent {}
    
    /**
     * Health check status update.
     */
    record HealthUpdate(String clientId, boolean healthy) implements OneirosEvent {}
}
