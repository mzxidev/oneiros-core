package io.oneiros.migration;

import io.oneiros.client.OneirosClient;
import reactor.core.publisher.Mono;

/**
 * Interface for versioned database migrations.
 *
 * <p>Similar to Flyway migrations, each migration has a version number and is executed
 * exactly once. The engine tracks applied migrations in the {@code oneiros_schema_history} table.
 *
 * <p><b>Example:</b>
 * <pre>
 * {@code
 * public class V001_CreateUserTable implements OneirosMigration {
 *
 *     @Override
 *     public int getVersion() {
 *         return 1;
 *     }
 *
 *     @Override
 *     public String getDescription() {
 *         return "Create user table with initial fields";
 *     }
 *
 *     @Override
 *     public Mono<Void> up(OneirosClient client) {
 *         return client.query(
 *             "DEFINE TABLE user SCHEMAFULL; " +
 *             "DEFINE FIELD name ON user TYPE string; " +
 *             "DEFINE FIELD email ON user TYPE string;"
 *         ).then();
 *     }
 * }
 * }
 * </pre>
 *
 * <p><b>Naming Convention:</b>
 * <ul>
 *   <li>{@code V001_Description} - Version 1</li>
 *   <li>{@code V002_Description} - Version 2</li>
 *   <li>{@code V010_Description} - Version 10</li>
 * </ul>
 *
 * <p><b>Best Practices:</b>
 * <ul>
 *   <li>Never modify an already-applied migration</li>
 *   <li>Always increment version numbers</li>
 *   <li>Make migrations idempotent when possible</li>
 *   <li>Test migrations in a staging environment first</li>
 * </ul>
 *
 * @see OneirosMigrationEngine
 * @see SchemaHistoryEntry
 */
public interface OneirosMigration {

    /**
     * Execute the migration.
     *
     * <p>This method is called exactly once per migration. If it throws an exception,
     * the migration is marked as failed and the engine stops.
     *
     * @param client The Oneiros client to execute statements
     * @return A Mono that completes when the migration is finished
     */
    Mono<Void> up(OneirosClient client);

    /**
     * Get the version number of this migration.
     *
     * <p>Versions must be unique positive integers. They determine the execution order.
     * Common convention: 1, 2, 3... or 001, 002, 003...
     *
     * @return The version number (must be > 0)
     */
    int getVersion();

    /**
     * Get a human-readable description of this migration.
     *
     * <p>This is stored in the schema history table for documentation purposes.
     *
     * @return A brief description of what this migration does
     */
    String getDescription();
}

