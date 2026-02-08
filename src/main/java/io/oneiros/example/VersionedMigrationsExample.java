package io.oneiros.example;

import io.oneiros.core.Oneiros;
import io.oneiros.core.OneirosBuilder;

/**
 * Example: Using versioned migrations (Flyway-style) with Oneiros.
 *
 * <p>This demonstrates:
 * <ul>
 *   <li>Setting up the migration package</li>
 *   <li>Automatic execution of pending migrations</li>
 *   <li>Schema history tracking</li>
 * </ul>
 *
 * <h2>Migration Structure:</h2>
 * <pre>
 * com.myapp.db/
 *   ├── migrations/
 *   │   ├── V001_CreateUserTable.java
 *   │   ├── V002_AddFullNameField.java
 *   │   └── V003_CreatePostsAndRelations.java
 *   └── schema/
 *       ├── UsersSchema.java (annotated with @OneirosTable)
 *       └── PostsSchema.java (annotated with @OneirosTable)
 * </pre>
 *
 * <h2>Execution Order:</h2>
 * <ol>
 *   <li>Create oneiros_schema_history table</li>
 *   <li>Check current database version</li>
 *   <li>Execute pending migrations (V001, V002, V003...)</li>
 *   <li>Sync schema definitions from @OneirosTable classes</li>
 * </ol>
 */
public class VersionedMigrationsExample {

    public static void main(String[] args) {
        System.out.println("🚀 Starting Oneiros with Versioned Migrations");

        // Create Oneiros instance with migrations enabled
        Oneiros oneiros = OneirosBuilder.create()
            .url("ws://localhost:8000/rpc")
            .namespace("myns")
            .database("mydb")
            .username("root")
            .password("root")

            // Enable migrations and specify package to scan
            .migrationsPackage("io.oneiros.example.migrations")
            .migrationDryRun(false)  // Set to true to preview migrations

            .build();

        System.out.println("✅ Oneiros started successfully");
        System.out.println("📊 Migration history is stored in: oneiros_schema_history");

        try {
            // Give it a moment to complete migrations
            Thread.sleep(2000);

            // You can now use the client
            oneiros.client()
                .query("SELECT * FROM oneiros_schema_history ORDER BY version", Object.class)
                .collectList()
                .subscribe(
                    history -> {
                        System.out.println("\n📋 Applied Migrations:");
                        history.forEach(entry -> System.out.println("   " + entry));
                    },
                    error -> System.err.println("❌ Error: " + error.getMessage())
                );

            // Wait for async operations
            Thread.sleep(1000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Clean up
            oneiros.close();
            System.out.println("\n👋 Oneiros closed");
        }
    }
}

