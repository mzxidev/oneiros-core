package io.oneiros.example;

import io.oneiros.core.Oneiros;
import io.oneiros.core.OneirosBuilder;
import io.oneiros.core.OneirosConfig;
import io.oneiros.migration.OneirosMigrationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example: Using Oneiros Migration Engine without Spring.
 *
 * This example shows how to:
 * 1. Create an Oneiros instance programmatically
 * 2. Run schema migrations for @OneirosTable classes
 * 3. Use the client for CRUD operations
 *
 * This is framework-agnostic and works in any Java application.
 */
public class StandaloneMigrationExample {

    private static final Logger log = LoggerFactory.getLogger(StandaloneMigrationExample.class);

    public static void main(String[] args) {
        // 1. Build Oneiros Client Configuration
        OneirosConfig config = OneirosConfig.builder()
            .url("ws://localhost:8000/rpc")
            .namespace("test")
            .database("myapp")
            .username("root")
            .password("root")
            .build();

        // 2. Create Oneiros instance
        Oneiros oneiros = OneirosBuilder.from(config)
            .poolEnabled(true)
            .poolSize(10)
            .circuitBreakerEnabled(true)
            .build();

        // 3. Connect to SurrealDB
        oneiros.client().connect()
            .doOnSuccess(v -> log.info("✅ Connected to SurrealDB"))
            .doOnError(e -> log.error("❌ Failed to connect", e))
            .block();

        // 4. Create Migration Engine
        OneirosMigrationEngine migrationEngine = new OneirosMigrationEngine(
            oneiros.client(),
            "io.oneiros.example.schema",  // Package to scan for @OneirosTable classes
            true,  // auto-migrate
            false, // not a dry-run
            false  // don't overwrite existing definitions
        );

        // 5. Run Migrations
        log.info("🔧 Starting schema migration...");
        migrationEngine.migrate()
            .doOnSuccess(v -> log.info("✅ Migration completed successfully"))
            .doOnError(e -> log.error("❌ Migration failed", e))
            .block();

        // 6. Now you can use the client for CRUD operations
        // (see other examples)

        // 7. Shutdown
        oneiros.close();
        log.info("👋 Disconnected from SurrealDB");
    }
}

