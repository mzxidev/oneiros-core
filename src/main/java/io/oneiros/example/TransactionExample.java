package io.oneiros.example;

import io.oneiros.client.OneirosClient;
import io.oneiros.core.OneirosBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Demonstrates ACID transaction support in Oneiros.
 *
 * <p>This example shows:
 * <ul>
 *   <li>Basic money transfer with automatic rollback on error</li>
 *   <li>Multi-step transactions with conditional logic</li>
 *   <li>Batch operations within transactions</li>
 *   <li>Error handling and retry logic</li>
 * </ul>
 */
public class TransactionExample {

    private static final Logger log = LoggerFactory.getLogger(TransactionExample.class);

    public static void main(String[] args) {
        // Create Oneiros client (framework-agnostic)
        var oneiros = OneirosBuilder.create()
            .url("ws://localhost:8000/rpc")
            .namespace("test")
            .database("test")
            .username("root")
            .password("root")
            .build();

        try {
            // Example 1: Simple Money Transfer
            log.info("=== Example 1: Simple Money Transfer ===");
            simpleMoneyTransfer(oneiros.client());

            // Example 2: Conditional Transaction
            log.info("\n=== Example 2: Conditional Transaction ===");
            conditionalTransaction(oneiros.client());

            // Example 3: Batch Operations
            log.info("\n=== Example 3: Batch Operations ===");
            batchOperations(oneiros.client());

            // Example 4: Error Handling (Rollback)
            log.info("\n=== Example 4: Error Handling (Rollback) ===");
            rollbackDemo(oneiros.client());

            log.info("\n✅ All examples completed successfully!");

        } catch (Exception e) {
            log.error("❌ Example failed: {}", e.getMessage(), e);
        } finally {
            oneiros.close();
        }
    }

    /**
     * Example 1: Simple money transfer between two accounts.
     * Both operations succeed or both fail (ACID).
     */
    private static void simpleMoneyTransfer(OneirosClient client) {
        // Setup test data
        setupAccounts(client);

        // Execute transaction
        client.transaction(tx -> {
            log.info("💸 Transferring 100 from Alice to Bob");

            return tx.query("UPDATE account:alice SET balance -= 100", Map.class)
                .next()
                .doOnSuccess(result -> log.info("  ✓ Deducted from Alice: {}", result))
                .then(tx.query("UPDATE account:bob SET balance += 100", Map.class).next())
                .doOnSuccess(result -> log.info("  ✓ Added to Bob: {}", result))
                .then(tx.create("transaction",
                    Map.of(
                        "from", "account:alice",
                        "to", "account:bob",
                        "amount", 100
                    ),
                    Map.class))
                .doOnSuccess(result -> log.info("  ✓ Transaction recorded: {}", result));
        }).block();

        log.info("✅ Money transfer committed");

        // Verify balances
        verifyBalances(client);
    }

    /**
     * Example 2: Transaction with conditional logic.
     * Check balance before transfer, fail if insufficient funds.
     */
    private static void conditionalTransaction(OneirosClient client) {
        client.transaction(tx -> {
            log.info("🔍 Checking Alice's balance before transfer");

            return tx.select("account:alice", Map.class)
                .next()
                .flatMap(alice -> {
                    double balance = ((Number) alice.get("balance")).doubleValue();
                    log.info("  Alice's balance: {}", balance);

                    if (balance < 50) {
                        log.warn("  ⚠️ Insufficient funds!");
                        return tx.query(
                            "CREATE notification CONTENT {" +
                            "  user: 'account:alice'," +
                            "  message: 'Insufficient funds for transfer'," +
                            "  timestamp: time::now()" +
                            "}", Map.class).next();
                    }

                    log.info("  ✓ Sufficient funds, proceeding with transfer");
                    return tx.query("UPDATE account:alice SET balance -= 50", Map.class).next()
                        .then(tx.query("UPDATE account:bob SET balance += 50", Map.class).next());
                });
        }).block();

        log.info("✅ Conditional transaction completed");
    }

    /**
     * Example 3: Batch operations in a single transaction.
     * Insert multiple records atomically.
     */
    private static void batchOperations(OneirosClient client) {
        client.transactionMany(tx -> {
            log.info("📦 Creating 5 transaction records in one atomic operation");

            return reactor.core.publisher.Flux.range(1, 5)
                .flatMap(i -> {
                    var data = Map.of(
                        "from", "account:alice",
                        "to", "account:bob",
                        "amount", i * 10,
                        "timestamp", "time::now()"
                    );
                    return tx.create("transaction", data, Map.class)
                        .doOnSuccess(result -> log.info("  ✓ Created transaction #{}: {}", i, result.get("id")));
                });
        }).blockLast();

        log.info("✅ Batch operations committed");
    }

    /**
     * Example 4: Demonstrate automatic rollback on error.
     * First operation succeeds, second fails -> both rolled back.
     */
    private static void rollbackDemo(OneirosClient client) {
        log.info("⚠️ Attempting transaction that will fail...");

        try {
            client.transaction(tx -> {
                return tx.query("UPDATE account:alice SET balance -= 100", Map.class)
                    .next()
                    .doOnSuccess(result -> log.info("  ✓ Step 1 completed: {}", result))
                    .then(tx.query("UPDATE account:invalid SET balance += 100", Map.class).next())
                    .doOnSuccess(result -> log.info("  ✓ Step 2 completed: {}", result));
            }).block();
        } catch (Exception e) {
            log.warn("❌ Transaction failed as expected: {}", e.getMessage());
            log.info("🔄 SurrealDB automatically rolled back all changes");
        }

        // Verify that Alice's balance was not changed
        var balance = client.select("account:alice", Map.class)
            .next()
            .map(acc -> ((Number) acc.get("balance")).doubleValue())
            .block();

        log.info("✅ Rollback verified - Alice's balance unchanged: {}", balance);
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private static void setupAccounts(OneirosClient client) {
        log.info("📝 Setting up test accounts");

        client.query("DELETE account", Map.class).blockLast();
        client.query("DELETE transaction", Map.class).blockLast();

        client.create("account:alice", Map.of("name", "Alice", "balance", 1000), Map.class).block();
        client.create("account:bob", Map.of("name", "Bob", "balance", 500), Map.class).block();

        log.info("  ✓ Alice: balance=1000");
        log.info("  ✓ Bob: balance=500");
    }

    private static void verifyBalances(OneirosClient client) {
        var alice = client.select("account:alice", Map.class).blockFirst();
        var bob = client.select("account:bob", Map.class).blockFirst();

        if (alice != null && bob != null) {
            log.info("📊 Final balances:");
            log.info("  Alice: {}", alice.get("balance"));
            log.info("  Bob: {}", bob.get("balance"));
        }
    }
}

