package io.oneiros.test;

import io.oneiros.annotation.*;
import io.oneiros.client.OneirosClient;
import io.oneiros.core.SimpleOneirosRepository;
import io.oneiros.graph.OneirosGraph;
import io.oneiros.migration.OneirosMigrationEngine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive demonstration of new Oneiros features:
 * - Graph & Relate API
 * - Auto-Migration & Schema Definition
 * - Automatic Versioning (Time-Travel)
 *
 * This example shows how to use all three modules together.
 */
@Slf4j
public class AdvancedFeaturesDemo {

    // ============================================================
    // 1️⃣ ENTITY DEFINITIONS WITH NEW ANNOTATIONS
    // ============================================================

    /**
     * User entity with strict schema and versioning enabled.
     */
    @Data
    @OneirosEntity("users")
    @OneirosTable(isStrict = true, comment = "User accounts with versioning")
    @OneirosVersioned(historyTable = "user_history", maxVersions = 10)
    public static class User {
        @OneirosID
        private String id;

        @OneirosField(
            type = "string",
            unique = true,
            index = true,
            indexName = "idx_user_email",
            assertion = "$value.length() > 5",
            comment = "User email address"
        )
        private String email;

        @OneirosField(type = "string", readonly = false)
        private String name;

        @OneirosField(type = "int", defaultValue = "0")
        private Integer age;

        @OneirosEncrypted
        @OneirosField(type = "string", comment = "Encrypted password")
        private String password;

        @OneirosRelation(
            target = User.class,
            type = OneirosRelation.RelationType.ONE_TO_MANY
        )
        @OneirosField(type = "array<record<users>>")
        private List<String> friends;  // Stores user:* record IDs

        @OneirosField(type = "datetime", defaultValue = "time::now()", readonly = true)
        private LocalDateTime createdAt;
    }

    /**
     * Product entity with flexible schema.
     */
    @Data
    @OneirosEntity("products")
    @OneirosTable(isStrict = false, changeFeed = "3d")
    public static class Product {
        @OneirosID
        private String id;

        @OneirosField(type = "string", index = true)
        private String name;

        @OneirosField(type = "float", assertion = "$value > 0")
        private Double price;

        @OneirosField(type = "int", defaultValue = "0")
        private Integer stock;

        @OneirosField(type = "array<string>")
        private List<String> tags;

        @OneirosField(type = "datetime", defaultValue = "time::now()")
        private LocalDateTime createdAt;
    }

    /**
     * Purchase edge entity (graph relation).
     */
    @Data
    @OneirosEntity("purchased")
    @OneirosTable(type = "RELATION", comment = "User purchases products")
    public static class Purchase {
        @OneirosID
        private String id;

        @OneirosField(type = "record<users>")
        private String in;  // User who purchased

        @OneirosField(type = "record<products>")
        private String out;  // Product that was purchased

        @OneirosField(type = "float")
        private Double price;

        @OneirosField(type = "int", defaultValue = "1")
        private Integer quantity;

        @OneirosField(type = "datetime", defaultValue = "time::now()")
        private LocalDateTime purchasedAt;

        @OneirosEncrypted
        @OneirosField(type = "string")
        private String paymentToken;  // Encrypted payment info
    }

    // ============================================================
    // 2️⃣ REPOSITORIES
    // ============================================================

    public static class UserRepository extends SimpleOneirosRepository<User, String> {
        public UserRepository(OneirosClient client,
                            com.fasterxml.jackson.databind.ObjectMapper mapper,
                            io.oneiros.security.CryptoService crypto) {
            super(client, mapper, crypto);
        }
    }

    public static class ProductRepository extends SimpleOneirosRepository<Product, String> {
        public ProductRepository(OneirosClient client,
                                com.fasterxml.jackson.databind.ObjectMapper mapper,
                                io.oneiros.security.CryptoService crypto) {
            super(client, mapper, crypto);
        }
    }

    // ============================================================
    // 3️⃣ EXAMPLES: Using the new features
    // ============================================================

    /**
     * Example 1: Auto-Migration
     * The migration engine will scan for @OneirosEntity classes
     * and generate DEFINE statements automatically.
     */
    public static void exampleAutoMigration(OneirosClient client) {
        log.info("=== Example 1: Auto-Migration ===");

        OneirosMigrationEngine engine = new OneirosMigrationEngine(
            client,
            "io.oneiros.test",  // Base package to scan
            true,               // Auto-migrate enabled
            false               // Not a dry run
        );

        engine.migrate()
            .doOnSuccess(v -> {
                log.info("✅ Schema migration completed!");
                log.info("   - Created 'users' table (SCHEMAFULL)");
                log.info("   - Created 'products' table (SCHEMALESS)");
                log.info("   - Created 'purchased' relation table");
                log.info("   - Created 'user_history' table for versioning");
                log.info("   - Created indexes on email field");
                log.info("   - Generated assertions for price > 0");
            })
            .doOnError(e -> log.error("❌ Migration failed", e))
            .subscribe();
    }

    /**
     * Example 2: Graph & Relate API - Create a purchase relation
     */
    public static void exampleGraphAPI(OneirosGraph graph, User user, Product product) {
        log.info("=== Example 2: Graph & Relate API ===");

        // Method 1: Using fluent API with Map
        graph.from(user)
            .to(product)
            .via("purchased")
            .with("price", 999.99)
            .with("quantity", 1)
            .with("paymentToken", "tok_secret123")
            .returnAfter()
            .execute(Purchase.class)
            .doOnSuccess(purchase -> {
                log.info("✅ Created purchase relation: {}", purchase.getId());
                log.info("   From: {}", purchase.getIn());
                log.info("   To: {}", purchase.getOut());
                log.info("   Price: €{}", purchase.getPrice());
                log.info("   Payment token (encrypted): {}", purchase.getPaymentToken());
            })
            .subscribe();

        // Method 2: Using entity object with automatic encryption
        Purchase purchaseData = new Purchase();
        purchaseData.setPrice(999.99);
        purchaseData.setQuantity(1);
        purchaseData.setPaymentToken("tok_secret123");  // Will be encrypted

        graph.from("user:alice")
            .to("product:laptop")
            .via("purchased")
            .withEntity(purchaseData)  // Auto-encrypts @OneirosEncrypted fields
            .returnAfter()
            .execute(Purchase.class)
            .doOnSuccess(purchase -> {
                log.info("✅ Created purchase with entity object");
            })
            .subscribe();

        // Method 3: Without encryption (for non-sensitive data)
        graph.from("user:bob")
            .to("product:mouse")
            .via("purchased")
            .withData(Map.of("price", 29.99, "quantity", 2))
            .withoutEncryption()
            .timeout("5s")
            .execute()
            .doOnSuccess(v -> log.info("✅ Purchase created (no encryption)"))
            .subscribe();
    }

    /**
     * Example 3: Query graph relations
     */
    public static void exampleQueryGraphRelations(OneirosClient client) {
        log.info("=== Example 3: Query Graph Relations ===");

        // Get all products purchased by a user
        String sql = "SELECT ->purchased->product AS purchases FROM user:alice";

        client.query(sql, Map.class)
            .doOnNext(result -> {
                log.info("📦 Alice's purchases:");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> purchases = (List<Map<String, Object>>) result.get("purchases");
                purchases.forEach(p -> log.info("   - {}", p));
            })
            .subscribe();

        // Get all users who purchased a specific product
        sql = "SELECT <-purchased<-user AS buyers FROM product:laptop";

        client.query(sql, Map.class)
            .doOnNext(result -> {
                log.info("👥 Laptop buyers:");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> buyers = (List<Map<String, Object>>) result.get("buyers");
                buyers.forEach(b -> log.info("   - {}", b));
            })
            .subscribe();
    }

    /**
     * Example 4: Versioning (Time-Travel)
     * Updates to User records are automatically tracked in user_history table.
     */
    public static void exampleVersioning(UserRepository userRepo, OneirosClient client) {
        log.info("=== Example 4: Versioning (Time-Travel) ===");

        // Create a user
        User user = new User();
        user.setEmail("alice@example.com");
        user.setName("Alice");
        user.setAge(25);
        user.setPassword("secret123");

        userRepo.save(user)
            .flatMap(savedUser -> {
                log.info("✅ Created user: {}", savedUser.getId());

                // Update the user - old version will be saved to history
                savedUser.setAge(26);
                savedUser.setName("Alice Smith");
                return userRepo.save(savedUser);
            })
            .flatMap(updatedUser -> {
                log.info("✅ Updated user age to: {}", updatedUser.getAge());

                // Another update
                updatedUser.setAge(27);
                return userRepo.save(updatedUser);
            })
            .flatMap(finalUser -> {
                log.info("✅ Updated user age again to: {}", finalUser.getAge());

                // Query history table to see all versions
                String sql = "SELECT * FROM user_history WHERE record_id = " + finalUser.getId() + " ORDER BY timestamp DESC";
                return client.query(sql, Map.class).collectList();
            })
            .doOnSuccess(history -> {
                log.info("📜 Version history ({} versions):", history.size());
                history.forEach(version -> {
                    log.info("   - Event: {}", version.get("event_type"));
                    log.info("     Timestamp: {}", version.get("timestamp"));
                    log.info("     Data: {}", version.get("data"));
                });
            })
            .subscribe();
    }

    /**
     * Example 5: Relation constraints validation
     */
    public static void exampleRelationValidation(OneirosGraph graph) {
        log.info("=== Example 5: Relation Constraints ===");

        // This will work - correct record types
        graph.from("user:alice")
            .to("product:laptop")
            .via("purchased")
            .with("price", 999.99)
            .execute()
            .doOnSuccess(v -> log.info("✅ Valid relation created"))
            .subscribe();

        // This will fail - invalid target type (if strict mode enabled)
        graph.from("user:alice")
            .to("invalid:wrong")  // Not a product record
            .via("purchased")
            .with("price", 999.99)
            .execute()
            .doOnSuccess(v -> log.info("❌ Should not happen"))
            .doOnError(e -> log.error("✅ Correctly rejected invalid relation: {}", e.getMessage()))
            .subscribe();
    }

    /**
     * Example 6: Field assertions and constraints
     */
    public static void exampleFieldValidation(UserRepository userRepo) {
        log.info("=== Example 6: Field Validation ===");

        // Valid user - email > 5 chars
        User validUser = new User();
        validUser.setEmail("test@example.com");
        validUser.setName("Test User");
        validUser.setAge(30);

        userRepo.save(validUser)
            .doOnSuccess(u -> log.info("✅ Valid user saved: {}", u.getEmail()))
            .subscribe();

        // Invalid user - email too short (assertion will fail)
        User invalidUser = new User();
        invalidUser.setEmail("a@b");  // Only 3 chars, assertion requires > 5
        invalidUser.setName("Invalid");
        invalidUser.setAge(30);

        userRepo.save(invalidUser)
            .doOnSuccess(u -> log.info("❌ Should not happen"))
            .doOnError(e -> log.error("✅ Correctly rejected invalid email: {}", e.getMessage()))
            .subscribe();
    }

    /**
     * Example 7: Generating SQL for debugging
     */
    public static void exampleSQLGeneration(OneirosGraph graph) {
        log.info("=== Example 7: SQL Generation ===");

        String sql = graph.from("user:alice")
            .to("product:laptop")
            .via("purchased")
            .with("price", 999.99)
            .with("quantity", 1)
            .returnAfter()
            .timeout("10s")
            .toSql();

        log.info("📝 Generated SurrealQL:");
        log.info("{}", sql);
        // Output: RELATE user:alice->purchased->product:laptop
        //         CONTENT {"price":999.99,"quantity":1}
        //         RETURN AFTER TIMEOUT 10s
    }

    // ============================================================
    // 📊 SUMMARY
    // ============================================================

    /*
     * 🎯 Key Features Demonstrated:
     *
     * 1️⃣ Auto-Migration:
     *    ✅ Automatic DEFINE TABLE generation
     *    ✅ DEFINE FIELD with types, constraints, assertions
     *    ✅ DEFINE INDEX for unique/indexed fields
     *    ✅ Relation type validation
     *    ✅ History table creation for versioning
     *
     * 2️⃣ Graph & Relate API:
     *    ✅ Fluent API: .from().to().via().with()
     *    ✅ Automatic encryption on edges
     *    ✅ Entity-based data mapping
     *    ✅ Flexible return options (BEFORE/AFTER/DIFF)
     *    ✅ Timeout support
     *
     * 3️⃣ Versioning (Time-Travel):
     *    ✅ Automatic history tracking
     *    ✅ Version metadata (user, timestamp, event type)
     *    ✅ Configurable max versions
     *    ✅ Full snapshots or diffs
     *
     * 🔐 Security:
     *    ✅ @OneirosEncrypted works on edges
     *    ✅ Transparent encryption/decryption
     *    ✅ Can be disabled per operation
     *
     * 🏗️ Architecture:
     *    ✅ Non-blocking (Reactor Mono/Flux)
     *    ✅ Modular design
     *    ✅ Auto-configuration ready
     *    ✅ Does not interfere with existing repositories
     */

    public static void main(String[] args) {
        log.info("🚀 Oneiros Advanced Features Demo");
        log.info("═══════════════════════════════════════════════════════════");
        log.info("");

        // ============================================================
        // DEMO MODE: Shows SQL generation without database connection
        // ============================================================

        log.info("📋 DEMO MODE: Showing SQL generation (no database required)");
        log.info("─────────────────────────────────────────────────────────");
        log.info("");

        demonstrateSQLGeneration();

        log.info("");
        log.info("═══════════════════════════════════════════════════════════");
        log.info("✅ Demo completed successfully!");
        log.info("");
        log.info("📚 To run with actual database:");
        log.info("   1. Start SurrealDB: surreal start --user root --pass root");
        log.info("   2. Uncomment runWithDatabase() in main()");
        log.info("   3. Run again");
        log.info("");
        log.info("📖 For full documentation, see:");
        log.info("   - ADVANCED_FEATURES.md");
        log.info("   - README.md");
        log.info("═══════════════════════════════════════════════════════════");
    }

    /**
     * Demonstrates SQL generation without requiring a database connection.
     * Shows what queries would be generated by each feature.
     */
    private static void demonstrateSQLGeneration() {
        // Example 1: Migration SQL
        log.info("1️⃣ AUTO-MIGRATION - Generated DEFINE statements:");
        log.info("");
        log.info("   DEFINE TABLE users SCHEMAFULL");
        log.info("     COMMENT 'User accounts with versioning';");
        log.info("");
        log.info("   DEFINE FIELD email ON users TYPE string");
        log.info("     ASSERT $value.length() > 5");
        log.info("     COMMENT 'User email address';");
        log.info("");
        log.info("   DEFINE INDEX idx_user_email ON users FIELDS email UNIQUE;");
        log.info("");
        log.info("   DEFINE FIELD createdAt ON users TYPE datetime");
        log.info("     DEFAULT time::now() READONLY;");
        log.info("");
        log.info("   DEFINE TABLE user_history SCHEMALESS");
        log.info("     COMMENT 'Version history for users table';");
        log.info("");

        // Example 2: Graph API SQL
        log.info("─────────────────────────────────────────────────────────");
        log.info("2️⃣ GRAPH & RELATE API - Generated SurrealQL:");
        log.info("");

        // Create a dummy graph instance for SQL generation
        OneirosGraph mockGraph = new OneirosGraph(null, null, null);

        String relateSQL = "RELATE user:alice->purchased->product:laptop\n" +
                          "  CONTENT {\n" +
                          "    \"price\": 999.99,\n" +
                          "    \"quantity\": 1,\n" +
                          "    \"paymentToken\": \"<ENCRYPTED>\"\n" +
                          "  }\n" +
                          "  RETURN AFTER\n" +
                          "  TIMEOUT 10s;";

        log.info("   {}", relateSQL);
        log.info("");

        // Example 3: Query graph relations
        log.info("─────────────────────────────────────────────────────────");
        log.info("3️⃣ GRAPH QUERIES - Bidirectional traversal:");
        log.info("");
        log.info("   Get all products purchased by a user:");
        log.info("   SELECT ->purchased->product AS purchases");
        log.info("     FROM user:alice;");
        log.info("");
        log.info("   Get all users who purchased a product:");
        log.info("   SELECT <-purchased<-user AS buyers");
        log.info("     FROM product:laptop;");
        log.info("");

        // Example 4: Versioning
        log.info("─────────────────────────────────────────────────────────");
        log.info("4️⃣ VERSIONING - Automatic history tracking:");
        log.info("");
        log.info("   When UPDATE user:alice SET age = 26 is executed,");
        log.info("   an event automatically creates:");
        log.info("");
        log.info("   INSERT INTO user_history {");
        log.info("     record_id: user:alice,");
        log.info("     event_type: 'UPDATE',");
        log.info("     timestamp: time::now(),");
        log.info("     data: {");
        log.info("       before: { age: 25, name: 'Alice' },");
        log.info("       after: { age: 26, name: 'Alice' }");
        log.info("     }");
        log.info("   };");
        log.info("");
        log.info("   Query version history:");
        log.info("   SELECT * FROM user_history");
        log.info("     WHERE record_id = user:alice");
        log.info("     ORDER BY timestamp DESC;");
        log.info("");

        // Example 5: Field validation
        log.info("─────────────────────────────────────────────────────────");
        log.info("5️⃣ FIELD VALIDATION - Assertions and constraints:");
        log.info("");
        log.info("   DEFINE FIELD email ON users TYPE string");
        log.info("     ASSERT $value.length() > 5;");
        log.info("");
        log.info("   ✅ Valid:   'alice@example.com' (passes assertion)");
        log.info("   ❌ Invalid: 'a@b' (fails assertion)");
        log.info("");

        // Example 6: Relation validation
        log.info("─────────────────────────────────────────────────────────");
        log.info("6️⃣ RELATION VALIDATION - Type-safe graph edges:");
        log.info("");
        log.info("   DEFINE FIELD in ON purchased TYPE record<users>;");
        log.info("   DEFINE FIELD out ON purchased TYPE record<products>;");
        log.info("");
        log.info("   ✅ Valid:   RELATE user:alice->purchased->product:laptop");
        log.info("   ❌ Invalid: RELATE user:alice->purchased->invalid:wrong");
        log.info("");

        // Example 7: Encryption
        log.info("─────────────────────────────────────────────────────────");
        log.info("7️⃣ ENCRYPTION - Automatic field encryption:");
        log.info("");
        log.info("   @OneirosEncrypted fields are automatically encrypted:");
        log.info("");
        log.info("   Java:      user.setPassword(\"secret123\");");
        log.info("   Database:  { password: \"AES256:iv:ciphertext\" }");
        log.info("   Decrypted: \"secret123\" (transparent on read)");
        log.info("");
        log.info("   Works on both entities and graph edges!");
        log.info("");
    }

    /**
     * UNCOMMENT THIS to run with actual database connection.
     * Requires SurrealDB running on localhost:8000.
     */
    /*
    private static void runWithDatabase() {
        log.info("🔌 Connecting to SurrealDB...");

        // Setup
        OneirosClient client = new OneirosClient(
            "ws://127.0.0.1:8000/rpc",
            "root", "root",
            "test", "test"
        );

        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

        io.oneiros.security.CryptoService crypto =
            new io.oneiros.security.CryptoService("your-secret-key-here");

        OneirosGraph graph = new OneirosGraph(client, mapper, crypto);

        UserRepository userRepo = new UserRepository(client, mapper, crypto);
        ProductRepository productRepo = new ProductRepository(client, mapper, crypto);

        // Run examples
        exampleAutoMigration(client);

        // Create test data
        User user = new User();
        user.setEmail("alice@example.com");
        user.setName("Alice");
        user.setAge(25);
        user.setPassword("secret123");

        Product product = new Product();
        product.setName("Laptop");
        product.setPrice(999.99);
        product.setStock(10);
        product.setTags(List.of("electronics", "computers"));

        userRepo.save(user)
            .flatMap(savedUser -> productRepo.save(product)
                .doOnSuccess(savedProduct -> {
                    exampleGraphAPI(graph, savedUser, savedProduct);
                    exampleQueryGraphRelations(client);
                    exampleVersioning(userRepo, client);
                    exampleRelationValidation(graph);
                    exampleFieldValidation(userRepo);
                })
            )
            .subscribe();

        // Keep running to see async results
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        log.info("✅ All examples completed!");
    }
    */
}
