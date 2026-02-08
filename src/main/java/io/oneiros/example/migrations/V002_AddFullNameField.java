package io.oneiros.example.migrations;

import io.oneiros.client.OneirosClient;
import io.oneiros.migration.OneirosMigration;
import reactor.core.publisher.Mono;

/**
 * Example of a data transformation migration (V002).
 *
 * <p>This migration adds a new field and populates it with computed values
 * from existing data. This demonstrates how to handle complex schema changes
 * that require data manipulation.
 *
 * <p><b>Use Case:</b> Add a "full_name" field that combines existing "name" fields.
 */
public class V002_AddFullNameField implements OneirosMigration {

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public String getDescription() {
        return "Add full_name field and populate from existing data";
    }

    @Override
    public Mono<Void> up(OneirosClient client) {
        // Step 1: Add the new field
        String addFieldSql = """
            DEFINE FIELD IF NOT EXISTS full_name ON user TYPE string;
            """;

        // Step 2: Populate the field with data from existing records
        String populateDataSql = """
            UPDATE user SET full_name = name WHERE full_name = NONE;
            """;

        // Execute sequentially
        return client.query(addFieldSql, Object.class)
            .then()
            .then(Mono.defer(() -> client.query(populateDataSql, Object.class).then()));
    }
}

