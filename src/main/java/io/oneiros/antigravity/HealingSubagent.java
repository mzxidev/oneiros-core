package io.oneiros.antigravity;

import io.oneiros.antigravity.event.OneirosEvent;
import io.oneiros.client.OneirosClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A specialized subagent that can heal its state by replaying failed or buffered operations
 * after a connection recovery.
 */
public class HealingSubagent extends Subagent {

    private final OneirosClient client;
    // SECURITY FIX: Limit replay buffer to prevent OOM
    private final int maxBufferSize = 5000;
    private final Queue<PendingOperation<?>> replayBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final int maxRetries = 5;

    public HealingSubagent(OneirosClient client) {
        this.client = client;
    }

    /**
     * Executes an operation with self-healing capabilities.
     * If the client is disconnected, the operation is buffered.
     */
    public <T> Mono<T> execute(String description, Mono<T> operation) {
        return operation
            .doOnError(err -> {
                if (replayBuffer.size() < maxBufferSize) {
                    log.warn("❌ Agent {} failed operation '{}': {}. Buffering for replay.", agentId, description, err.getMessage());
                    replayBuffer.add(new PendingOperation<>(description, operation));
                } else {
                    log.error("💥 Agent {} buffer full! Dropping operation: {}", agentId, description);
                }
            })
            .retryWhen(reactor.util.retry.Retry.backoff(3, Duration.ofSeconds(1))
                .doBeforeRetry(signal -> log.info("🔄 Agent {} retrying '{}' (attempt {})", agentId, description, signal.totalRetries() + 1)));
    }

    @Override
    protected void onConnected(OneirosEvent.Connected event) {
        log.info("💚 Agent {} detected recovery. Replaying {} buffered operations...", agentId, replayBuffer.size());
        
        while (!replayBuffer.isEmpty()) {
            PendingOperation<?> op = replayBuffer.poll();
            if (op != null) {
                log.debug("📤 Replaying: {}", op.description);
                op.operation.subscribe(
                    result -> log.debug("✅ Replay successful: {}", op.description),
                    err -> {
                        log.error("💥 Replay failed for '{}': {}", op.description, err.getMessage());
                        // If it fails again during replay, we might want to put it back or alert
                    }
                );
            }
        }
    }

    @Override
    protected void onDisconnected(OneirosEvent.Disconnected event) {
        log.warn("⚠️ Agent {} entering standby mode due to disconnection: {}", agentId, event.cause().getMessage());
    }

    @Override
    protected void onTransactionError(OneirosEvent.TransactionFailed event) {
        super.onTransactionError(event);
        // Specialized logic for transaction healing
        log.info("🔧 Agent {} attempting to heal transaction {}", agentId, event.transactionId());
    }

    private record PendingOperation<T>(String description, Mono<T> operation) {}
}
