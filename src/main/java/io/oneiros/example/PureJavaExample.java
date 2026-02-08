package io.oneiros.example;

import io.oneiros.annotation.*;
import io.oneiros.core.Oneiros;
import io.oneiros.core.OneirosConfig;
import io.oneiros.security.EncryptionType;

import java.time.Instant;
import java.util.List;

/**
 * Example demonstrating how to use Oneiros without Spring Boot.
 * This is a standalone Java application.
 *
 * <p>To run this example:
 * <ol>
 *   <li>Start SurrealDB: {@code surreal start --user root --pass root}</li>
 *   <li>Run this main method</li>
 * </ol>
 */
public class PureJavaExample {

    // ========== Entity Definitions ==========

    /**
     * Example User entity with annotations.
     */
    @OneirosEntity("users")
    public static class User {
        @OneirosID
        private String id;

        @OneirosField(type = "string")
        private String name;

        @OneirosField(type = "string")
        private String email;

        @OneirosEncrypted(type = EncryptionType.ARGON2)
        private String password;

        @OneirosField(type = "int", defaultValue = "0")
        private int age;

        @OneirosField(type = "datetime", defaultValue = "time::now()")
        private Instant createdAt;

        // Constructors
        public User() {}

        public User(String name, String email, String password, int age) {
            this.name = name;
            this.email = email;
            this.password = password;
            this.age = age;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

        @Override
        public String toString() {
            return "User{id='" + id + "', name='" + name + "', email='" + email + "', age=" + age + "}";
        }
    }

    /**
     * Example Product entity.
     */
    @OneirosEntity("products")
    public static class Product {
        @OneirosID
        private String id;

        @OneirosField(type = "string")
        private String name;

        @OneirosField(type = "float", assertion = "$value > 0")
        private double price;

        @OneirosField(type = "int", defaultValue = "0")
        private int stock;

        public Product() {}

        public Product(String name, double price, int stock) {
            this.name = name;
            this.price = price;
            this.stock = stock;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }

        @Override
        public String toString() {
            return "Product{id='" + id + "', name='" + name + "', price=" + price + ", stock=" + stock + "}";
        }
    }

    // ========== Main Application ==========

    public static void main(String[] args) {
        System.out.println("🌙 Oneiros Pure Java Example");
        System.out.println("============================\n");

        // Method 1: Using the builder directly
        runWithBuilder();

        // Method 2: Using OneirosConfig object
        // runWithConfig();

        // Method 3: Using connection pool
        // runWithPool();
    }

    /**
     * Example using the fluent builder.
     */
    private static void runWithBuilder() {
        System.out.println("📌 Running with Oneiros.builder()...\n");

        // Create Oneiros instance
        try (Oneiros oneiros = Oneiros.builder()
                .url("ws://localhost:8000/rpc")
                .namespace("example")
                .database("pureJava")
                .username("root")
                .password("root")
                .autoConnect(false)  // We'll connect manually
                .migrationEnabled(true)
                .migrationBasePackage("io.oneiros.example")
                .securityEnabled(true)
                .securityKey("my-super-secret-key-for-encryption!!")
                .circuitBreakerEnabled(true)
                .build()) {

            // Connect to SurrealDB
            System.out.println("🔌 Connecting to SurrealDB...");
            oneiros.connectBlocking();
            System.out.println("✅ Connected!\n");

            // Run migrations
            System.out.println("🔧 Running schema migrations...");
            oneiros.migrateBlocking();
            System.out.println("✅ Migrations complete!\n");

            // Create some data
            System.out.println("📝 Creating test data...");

            // Create a user
            oneiros.client().query(
                "CREATE users SET name = 'John Doe', email = 'john@example.com', age = 30",
                User.class
            ).blockFirst();

            // Create some products
            oneiros.client().query(
                "CREATE products SET name = 'Laptop', price = 999.99, stock = 50",
                Product.class
            ).blockFirst();

            oneiros.client().query(
                "CREATE products SET name = 'Mouse', price = 29.99, stock = 200",
                Product.class
            ).blockFirst();

            System.out.println("✅ Test data created!\n");

            // Query data
            System.out.println("🔍 Querying users...");
            List<User> users = oneiros.client()
                .query("SELECT * FROM users", User.class)
                .collectList()
                .block();

            users.forEach(System.out::println);

            System.out.println("\n🔍 Querying products...");
            List<Product> products = oneiros.client()
                .query("SELECT * FROM products WHERE price < 100", Product.class)
                .collectList()
                .block();

            products.forEach(System.out::println);

            // Clean up test data
            System.out.println("\n🧹 Cleaning up...");
            oneiros.client().query("DELETE users", Object.class).blockLast();
            oneiros.client().query("DELETE products", Object.class).blockLast();

            System.out.println("\n✅ Example completed successfully!");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Example using OneirosConfig object.
     */
    private static void runWithConfig() {
        System.out.println("📌 Running with OneirosConfig...\n");

        // Create configuration object
        OneirosConfig config = OneirosConfig.builder()
            .url("ws://localhost:8000/rpc")
            .namespace("example")
            .database("pureJava")
            .username("root")
            .password("root")
            .autoConnect(true)
            .security(OneirosConfig.SecurityConfig.builder()
                .enabled(false)
                .build())
            .migration(OneirosConfig.MigrationConfig.builder()
                .enabled(false)
                .build())
            .build();

        // Create Oneiros from config
        try (Oneiros oneiros = Oneiros.create(config)) {
            // Use the client...
            System.out.println("✅ Connected with OneirosConfig!");
            System.out.println("   URL: " + oneiros.config().getUrl());
            System.out.println("   Namespace: " + oneiros.config().getNamespace());
            System.out.println("   Database: " + oneiros.config().getDatabase());
        }
    }

    /**
     * Example with connection pooling.
     */
    private static void runWithPool() {
        System.out.println("📌 Running with Connection Pool...\n");

        try (Oneiros oneiros = Oneiros.builder()
                .url("ws://localhost:8000/rpc")
                .namespace("example")
                .database("pureJava")
                .username("root")
                .password("root")
                .poolEnabled(true)
                .poolSize(5)
                .poolAutoReconnect(true)
                .build()) {

            System.out.println("✅ Connection pool created with 5 connections!");

            // Parallel queries will be load-balanced across connections
            System.out.println("🔄 Running parallel queries...");

            reactor.core.publisher.Flux.range(1, 10)
                .flatMap(i -> oneiros.client()
                    .query("SELECT * FROM type::thing('test', " + i + ")", Object.class)
                    .doOnSubscribe(s -> System.out.println("  Query " + i + " started"))
                )
                .blockLast();

            System.out.println("✅ All queries completed!");
        }
    }
}

