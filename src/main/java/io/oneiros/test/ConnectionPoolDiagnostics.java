package io.oneiros.test;

import io.oneiros.pool.OneirosConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates Connection Pool troubleshooting and diagnostics.
 * <p>
 * This class shows how to:
 * - Check pool initialization
 * - Monitor pool health
 * - Diagnose connection issues
 * - Verify configuration
 */
public class ConnectionPoolDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPoolDiagnostics.class);

    /**
     * Diagnoses connection pool issues and provides troubleshooting steps.
     */
    public static void diagnose(OneirosConnectionPool pool) {
        log.info("🔍 Running Connection Pool Diagnostics...");
        log.info("═══════════════════════════════════════════════════════════");

        // Step 1: Check if pool is initialized
        checkInitialization(pool);

        // Step 2: Check pool statistics
        checkPoolStats(pool);

        // Step 3: Verify connection health
        checkConnectionHealth(pool);

        // Step 4: Provide recommendations
        provideRecommendations(pool);

        log.info("═══════════════════════════════════════════════════════════");
        log.info("✅ Diagnostics completed");
    }

    private static void checkInitialization(OneirosConnectionPool pool) {
        log.info("\n1️⃣ Checking Pool Initialization...");

        if (pool == null) {
            log.error("❌ CRITICAL: Pool is null");
            log.error("   → Make sure oneiros.pool.enabled=true in application.yml");
            log.error("   → Verify OneirosAutoConfiguration is loaded");
            return;
        }

        if (!pool.isConnected()) {
            log.warn("⚠️ WARNING: Pool is not connected");
            log.warn("   → Check if SurrealDB is running");
            log.warn("   → Verify connection URL: ws://localhost:8000/rpc");
            log.warn("   → Check credentials (username/password)");
        } else {
            log.info("✅ Pool is initialized and connected");
        }
    }

    private static void checkPoolStats(OneirosConnectionPool pool) {
        log.info("\n2️⃣ Checking Pool Statistics...");

        OneirosConnectionPool.PoolStats stats = pool.getStats();

        log.info("   📊 Total connections: {}", stats.total());
        log.info("   ✅ Healthy connections: {}", stats.healthy());
        log.info("   ❌ Unhealthy connections: {}", stats.unhealthy());
        log.info("   🎯 Max pool size: {}", stats.maxSize());
        log.info("   📈 Health percentage: {:.1f}%", stats.healthPercentage());

        // Analyze stats
        if (stats.total() == 0) {
            log.error("❌ CRITICAL: No connections in pool!");
            log.error("   → Pool initialization failed");
            log.error("   → Check SurrealDB connection settings");
            log.error("   → Enable debug logging: logging.level.io.oneiros=DEBUG");
        } else if (stats.healthy() == 0) {
            log.error("❌ CRITICAL: No healthy connections!");
            log.error("   → All connections failed health check");
            log.error("   → Check network connectivity");
            log.error("   → Verify SurrealDB server status");
        } else if (stats.healthy() < stats.maxSize() * 0.5) {
            log.warn("⚠️ WARNING: Less than 50% connections healthy");
            log.warn("   → Some connections are experiencing issues");
            log.warn("   → Auto-recovery should fix this automatically");
        } else {
            log.info("✅ Pool health is good ({:.1f}%)", stats.healthPercentage());
        }
    }

    private static void checkConnectionHealth(OneirosConnectionPool pool) {
        log.info("\n3️⃣ Checking Connection Health...");

        try {
            pool.ping().block();
            log.info("✅ Successfully pinged SurrealDB");
        } catch (Exception e) {
            log.error("❌ Failed to ping SurrealDB: {}", e.getMessage());
            log.error("   → Connection is not working");
            log.error("   → Check if SurrealDB is running:");
            log.error("      $ surreal start --user root --pass root");
        }
    }

    private static void provideRecommendations(OneirosConnectionPool pool) {
        log.info("\n4️⃣ Recommendations...");

        OneirosConnectionPool.PoolStats stats = pool.getStats();

        if (stats.total() == 0) {
            log.info("🔧 Fix: Ensure pool is initialized at startup");
            log.info("   1. Add to application.yml:");
            log.info("      oneiros:");
            log.info("        pool:");
            log.info("          enabled: true");
            log.info("          size: 10");
            log.info("   2. Start SurrealDB:");
            log.info("      $ surreal start --user root --pass root");
            log.info("   3. Restart your application");
        } else if (stats.healthy() < stats.total()) {
            log.info("🔄 Auto-recovery is active");
            log.info("   → Unhealthy connections will be reconnected");
            log.info("   → Health checks run every 30 seconds");
            log.info("   → Monitor logs for recovery progress");
        } else {
            log.info("✅ Pool is healthy - no action needed");
        }

        // Configuration tips
        log.info("\n💡 Configuration Tips:");
        log.info("   • Development: pool.size=3-5");
        log.info("   • Production: pool.size=10-20");
        log.info("   • High traffic: pool.size=20-50");
        log.info("\n📚 See: CONNECTION_POOL_GUIDE.md for detailed setup");
    }

    /**
     * Generates a configuration checklist.
     */
    public static void generateConfigChecklist() {
        log.info("\n📋 Configuration Checklist:");
        log.info("═══════════════════════════════════════════════════════════");

        log.info("\n✓ Essential Settings:");
        log.info("  □ oneiros.url set to ws://localhost:8000/rpc");
        log.info("  □ oneiros.username and oneiros.password configured");
        log.info("  □ oneiros.namespace and oneiros.database set");
        log.info("  □ oneiros.auto-connect=true (or connect manually)");

        log.info("\n✓ Connection Pool Settings:");
        log.info("  □ oneiros.pool.enabled=true");
        log.info("  □ oneiros.pool.size set appropriately (5-20)");
        log.info("  □ oneiros.pool.auto-reconnect=true");
        log.info("  □ oneiros.pool.health-check-interval configured");

        log.info("\n✓ SurrealDB Server:");
        log.info("  □ SurrealDB is running");
        log.info("  □ WebSocket port 8000 is accessible");
        log.info("  □ Credentials are correct");

        log.info("\n✓ Spring Boot:");
        log.info("  □ @EnableScheduling present (for health checks)");
        log.info("  □ WebFlux dependency included");
        log.info("  □ Reactive repository support enabled");

        log.info("═══════════════════════════════════════════════════════════");
    }
}
