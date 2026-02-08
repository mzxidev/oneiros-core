package io.oneiros.example.migrations;

import io.oneiros.client.OneirosClient;
import io.oneiros.migration.OneirosMigration;
import reactor.core.publisher.Mono;

/**
 * Example of a versioned migration (V001).
 *
 * <p>This migration creates the initial user table with basic fields.
 *
 * <p><b>Naming Convention:</b> V{version}_{Description}
 * <ul>
 *   <li>V001 = Version 1</li>
 *   <li>V002 = Version 2</li>
 *   <li>etc.</li>
 * </ul>
 *
 * <p><b>Best Practices:</b>
 * <ul>
 *   <li>Never modify this migration after it has been applied to production</li>
 *   <li>Create a new migration (V002, V003, etc.) for changes</li>
 *   <li>Make migrations idempotent when possible (use IF NOT EXISTS)</li>
 * </ul>
 */
public class V001_CreateUserTable implements OneirosMigration {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Create user table with basic fields";
    }

    @Override
    public Mono<Void> up(OneirosClient client) {
        // Define the user table with initial schema
        String sql = """
            DEFINE TABLE IF NOT EXISTS user SCHEMAFULL;
            DEFINE FIELD IF NOT EXISTS name ON user TYPE string;
            DEFINE FIELD IF NOT EXISTS email ON user TYPE string ASSERT string::len($value) > 5;
            DEFINE FIELD IF NOT EXISTS created_at ON user TYPE datetime DEFAULT time::now();
            DEFINE INDEX IF NOT EXISTS idx_user_email ON user FIELDS email UNIQUE;
            """;

        return client.query(sql, Object.class).then();
    }
}

