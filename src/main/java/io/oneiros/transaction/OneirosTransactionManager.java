package io.oneiros.transaction;

import io.oneiros.client.OneirosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;

/**
 * Manages ACID transactions for SurrealDB using the Reactor Mono.usingWhen pattern.
 *
 * <p>This class ensures proper resource management:
 * <ol>
 *   <li>BEGIN TRANSACTION is sent before user code execution</li>
 *   <li>User code executes on a dedicated connection</li>
 *   <li>COMMIT TRANSACTION is sent on successful completion</li>
 *   <li>CANCEL TRANSACTION is sent on any error</li>
 * </ol>
 *
 * <p>Transaction isolation uses SurrealDB's optimistic concurrency control.
 * If conflicts occur, the entire transaction fails and must be retried.
 */
public class OneirosTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(OneirosTransactionManager.class);

    private final OneirosClient client;

    /**
     * Creates a transaction manager for the given client.
     *
     * @param client The OneirosClient to use for transactions
     */
    public OneirosTransactionManager(OneirosClient client) {
        this.client = client;
    }

    /**
     * Executes a block of code within an ACID transaction.
     *
     * <p><strong>Usage Example:</strong>
     * <pre>{@code
     * transactionManager.execute(tx -> {
     *     return tx.query("UPDATE account:A SET balance -= 100", Map.class)
     *              .thenMany(tx.query("UPDATE account:B SET balance += 100", Map.class))
     *              .collectList();
     * }).subscribe(
     *     results -> log.info("✅ Transaction committed: {}", results),
     *     error -> log.error("❌ Transaction rolled back: {}", error.getMessage())
     * );
     * }</pre>
     *
     * <p><strong>Guarantees:</strong>
     * <ul>
     *   <li>All operations execute on the same WebSocket connection</li>
     *   <li>Operations are serialized (sequential execution)</li>
     *   <li>Automatic rollback on any error</li>
     *   <li>Automatic commit on successful completion</li>
     * </ul>
     *
     * @param transactionBlock User code to execute within the transaction
     * @param <T> Result type
     * @return Mono that emits the transaction result or error
     */
    public <T> Mono<T> execute(Function<OneirosTransaction, Mono<T>> transactionBlock) {
        log.info("🔄 Starting transaction");

        // Get a dedicated connection (important for pooled clients)
        OneirosClient dedicatedClient = client.dedicated();

        return Mono.usingWhen(
            // 1. Resource Allocation: BEGIN TRANSACTION
            beginTransaction(dedicatedClient),

            // 2. User Code Execution: Execute the transaction block
            tx -> {
                log.debug("📝 Executing transaction block");
                return transactionBlock.apply(tx)
                    .doOnSuccess(result -> log.debug("✅ Transaction block completed successfully"))
                    .doOnError(error -> log.error("❌ Transaction block failed: {}", error.getMessage()));
            },

            // 3. Success Cleanup: COMMIT TRANSACTION
            tx -> commitTransaction(dedicatedClient)
                .doOnSuccess(v -> log.info("✅ Transaction committed"))
                .doOnError(error -> log.error("❌ Commit failed: {}", error.getMessage())),

            // 4. Error Cleanup: CANCEL TRANSACTION (on user code error)
            (tx, error) -> cancelTransaction(dedicatedClient)
                .doOnSuccess(v -> log.warn("🔄 Transaction rolled back due to error: {}", error.getMessage()))
                .doOnError(cancelError -> log.error("❌ Rollback failed: {}", cancelError.getMessage())),

            // 5. Cancel Cleanup: CANCEL TRANSACTION (on subscription cancel)
            tx -> cancelTransaction(dedicatedClient)
                .doOnSuccess(v -> log.warn("🔄 Transaction rolled back due to cancellation"))
                .doOnError(error -> log.error("❌ Rollback on cancel failed: {}", error.getMessage()))
        );
    }

    /**
     * Executes a block of code within an ACID transaction (Flux variant).
     *
     * <p>Use this when your transaction block returns multiple results (Flux).
     *
     * <p><strong>Usage Example:</strong>
     * <pre>{@code
     * transactionManager.executeMany(tx -> {
     *     return tx.query("UPDATE account:A SET balance -= 100", Map.class)
     *              .concatWith(tx.query("UPDATE account:B SET balance += 100", Map.class));
     * }).subscribe(
     *     result -> log.info("Transaction result: {}", result),
     *     error -> log.error("Transaction failed: {}", error.getMessage()),
     *     () -> log.info("✅ Transaction committed")
     * );
     * }</pre>
     *
     * @param transactionBlock User code to execute within the transaction
     * @param <T> Result type
     * @return Flux that emits transaction results or error
     */
    public <T> Flux<T> executeMany(Function<OneirosTransaction, Flux<T>> transactionBlock) {
        log.info("🔄 Starting transaction (Flux)");

        OneirosClient dedicatedClient = client.dedicated();

        return Flux.usingWhen(
            // 1. Resource Allocation: BEGIN TRANSACTION
            beginTransaction(dedicatedClient),

            // 2. User Code Execution
            tx -> {
                log.debug("📝 Executing transaction block (Flux)");
                return transactionBlock.apply(tx)
                    .doOnComplete(() -> log.debug("✅ Transaction block completed"))
                    .doOnError(error -> log.error("❌ Transaction block failed: {}", error.getMessage()));
            },

            // 3. Success Cleanup: COMMIT TRANSACTION
            tx -> commitTransaction(dedicatedClient)
                .doOnSuccess(v -> log.info("✅ Transaction committed"))
                .doOnError(error -> log.error("❌ Commit failed: {}", error.getMessage())),

            // 4. Error Cleanup: CANCEL TRANSACTION
            (tx, error) -> cancelTransaction(dedicatedClient)
                .doOnSuccess(v -> log.warn("🔄 Transaction rolled back due to error: {}", error.getMessage()))
                .doOnError(cancelError -> log.error("❌ Rollback failed: {}", cancelError.getMessage())),

            // 5. Cancel Cleanup
            tx -> cancelTransaction(dedicatedClient)
                .doOnSuccess(v -> log.warn("🔄 Transaction rolled back due to cancellation"))
                .doOnError(error -> log.error("❌ Rollback on cancel failed: {}", error.getMessage()))
        );
    }

    // ============================================================
    // INTERNAL: Transaction Control
    // ============================================================

    /**
     * Sends BEGIN TRANSACTION and returns a transaction context.
     */
    private Mono<OneirosTransaction> beginTransaction(OneirosClient dedicatedClient) {
        return dedicatedClient.query("BEGIN TRANSACTION", Map.class)
            .collectList()
            .doOnSuccess(v -> log.debug("📤 BEGIN TRANSACTION sent"))
            .doOnError(error -> log.error("❌ BEGIN TRANSACTION failed: {}", error.getMessage()))
            .thenReturn(new OneirosTransactionImpl(dedicatedClient));
    }

    /**
     * Sends COMMIT TRANSACTION.
     */
    private Mono<Void> commitTransaction(OneirosClient dedicatedClient) {
        return dedicatedClient.query("COMMIT TRANSACTION", Map.class)
            .collectList()
            .doOnSuccess(v -> log.debug("📤 COMMIT TRANSACTION sent"))
            .doOnError(error -> log.error("❌ COMMIT TRANSACTION failed: {}", error.getMessage()))
            .then();
    }

    /**
     * Sends CANCEL TRANSACTION.
     */
    private Mono<Void> cancelTransaction(OneirosClient dedicatedClient) {
        return dedicatedClient.query("CANCEL TRANSACTION", Map.class)
            .collectList()
            .doOnSuccess(v -> log.debug("📤 CANCEL TRANSACTION sent"))
            .doOnError(error -> log.error("❌ CANCEL TRANSACTION failed: {}", error.getMessage()))
            .then()
            .onErrorResume(error -> {
                // If cancel fails, log but don't propagate (we're already in error handling)
                log.error("⚠️ CANCEL TRANSACTION failed, connection may be in inconsistent state", error);
                return Mono.empty();
            });
    }
}

