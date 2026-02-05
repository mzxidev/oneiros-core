package io.oneiros.test;

import io.oneiros.annotation.OneirosEncrypted;
import io.oneiros.annotation.OneirosEntity;
import io.oneiros.annotation.OneirosID;
import io.oneiros.client.OneirosClient;
import io.oneiros.core.SimpleOneirosRepository;
import io.oneiros.query.OneirosQuery;
import io.oneiros.statement.statements.SelectStatement;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ✨ Demonstration: Automatic Object Conversion in Oneiros
 *
 * This example shows how query results are automatically converted
 * from SurrealDB JSON responses to Java objects.
 */
@Slf4j
public class AutoConversionExample {

    // ============================================================
    // 1️⃣ ENTITY DEFINITION
    // ============================================================

    @Data
    @OneirosEntity("products")
    public static class Product {
        @OneirosID
        private String id;

        private String name;
        private Double price;
        private Integer stock;
        private List<String> tags;
        private LocalDateTime createdAt;

        @OneirosEncrypted
        private String supplierKey;  // Will be encrypted automatically
    }

    // ============================================================
    // 2️⃣ REPOSITORY (extends SimpleOneirosRepository)
    // ============================================================

    public static class ProductRepository extends SimpleOneirosRepository<Product, String> {
        public ProductRepository(OneirosClient client,
                                com.fasterxml.jackson.databind.ObjectMapper mapper,
                                io.oneiros.security.CryptoService crypto) {
            super(client, mapper, crypto);
        }

        /**
         * Custom query method - Result is automatically converted to Product objects
         */
        public Flux<Product> findExpensiveProducts(double minPrice) {
            String sql = "SELECT * FROM products WHERE price >= " + minPrice;
            return client.query(sql, Product.class)  // ✨ Auto-converts JSON → Product
                    .map(product -> {
                        log.info("📦 Loaded: {} (€{})", product.getName(), product.getPrice());
                        return product;
                    });
        }
    }

    // ============================================================
    // 3️⃣ EXAMPLES: Different ways to query with auto-conversion
    // ============================================================

    /**
     * Example 1: Using Repository Pattern (High-Level)
     */
    public static void exampleRepositoryPattern(ProductRepository repo) {
        log.info("=== Example 1: Repository Pattern ===");

        // Save - automatically converts Java → JSON → SurrealDB
        Product product = new Product();
        product.setName("Laptop");
        product.setPrice(999.99);
        product.setStock(10);
        product.setTags(List.of("electronics", "computers"));
        product.setCreatedAt(LocalDateTime.now());
        product.setSupplierKey("SECRET-SUPPLIER-123");

        repo.save(product)
            .doOnSuccess(saved -> {
                // ✅ 'saved' is a fully converted Product object
                log.info("✅ Saved: {}", saved.getName());
                log.info("   ID: {}", saved.getId());
                log.info("   Encrypted field is transparent: {}", saved.getSupplierKey());
            })
            .subscribe();

        // Find by ID - automatically converts SurrealDB → JSON → Product
        repo.findById("product:laptop_pro")
            .doOnSuccess(found -> {
                // ✅ 'found' is a Product object (or null if not exists)
                if (found != null) {
                    log.info("✅ Found: {} - Stock: {}", found.getName(), found.getStock());
                }
            })
            .subscribe();

        // Find All - returns Flux<Product> (stream of Product objects)
        repo.findAll()
            .collectList()
            .doOnSuccess(products -> {
                // ✅ 'products' is List<Product>
                log.info("✅ Found {} products total", products.size());
                products.forEach(p -> log.info("   - {} (€{})", p.getName(), p.getPrice()));
            })
            .subscribe();

        // Custom query - automatic conversion
        repo.findExpensiveProducts(500.0)
            .collectList()
            .doOnSuccess(expensive -> {
                // ✅ 'expensive' is List<Product>
                log.info("✅ Found {} expensive products", expensive.size());
            })
            .subscribe();
    }

    /**
     * Example 2: Using Fluent Query API
     */
    public static void exampleFluentQueryAPI(OneirosClient client) {
        log.info("=== Example 2: Fluent Query API ===");

        OneirosQuery<Product> query = OneirosQuery.select(Product.class)
                .where("price").gte(100.0)
                .and("stock").gt(0)
                .orderBy("price DESC")
                .limit(10);

        String sql = query.toSql();
        log.info("📝 Generated SQL: {}", sql);

        // Execute and get auto-converted results
        client.query(sql, Product.class)
            .collectList()
            .doOnSuccess(products -> {
                // ✅ 'products' is automatically List<Product>
                log.info("✅ Query returned {} products", products.size());
                products.forEach(p ->
                    log.info("   - {} (€{}) - Stock: {}",
                        p.getName(), p.getPrice(), p.getStock())
                );
            })
            .subscribe();
    }

    /**
     * Example 3: Using Statement API
     */
    public static void exampleStatementAPI(OneirosClient client) {
        log.info("=== Example 3: Statement API ===");

        // Using direct SQL with query builder
        String sql = "SELECT * FROM products WHERE 'electronics' IN tags LIMIT 5";

        log.info("📝 Generated SQL: {}", sql);

        // Execute and get auto-converted results
        client.query(sql, Product.class)
            .collectList()
            .doOnSuccess(products -> {
                // ✅ All fields are automatically populated
                log.info("✅ Found {} electronic products", products.size());
                products.forEach(p -> {
                    log.info("   Product: {}", p.getName());
                    log.info("   - Price: €{}", p.getPrice());
                    log.info("   - Tags: {}", p.getTags());
                    log.info("   - Created: {}", p.getCreatedAt());
                });
            })
            .subscribe();
    }

    /**
     * Example 4: Complex nested objects (automatic deep conversion)
     */
    @Data
    @OneirosEntity("orders")
    public static class Order {
        @OneirosID
        private String id;

        private String customerId;
        private List<OrderItem> items;  // ✨ Nested objects are auto-converted!
        private OrderStatus status;     // ✨ Enums are auto-converted!
        private PaymentInfo payment;    // ✨ Nested objects work recursively!
        private LocalDateTime orderDate;
    }

    @Data
    public static class OrderItem {
        private String productId;
        private Integer quantity;
        private Double pricePerUnit;
    }

    public enum OrderStatus {
        PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
    }

    @Data
    public static class PaymentInfo {
        private String method;          // "credit_card", "paypal", etc.
        private Boolean paid;
        private LocalDateTime paidAt;
    }

    public static void exampleNestedObjects(OneirosClient client) {
        log.info("=== Example 4: Nested Objects ===");

        // Query returns complex nested structure - ALL automatically converted!
        String sql = "SELECT * FROM orders WHERE status = 'PENDING'";

        client.query(sql, Order.class)
            .take(1)
            .doOnNext(order -> {
                // ✅ ALL fields are automatically converted:
                log.info("✅ Order: {}", order.getId());
                log.info("   Customer: {}", order.getCustomerId());
                log.info("   Status: {}", order.getStatus()); // Enum converted!
                log.info("   Items:");

                // ✅ List<OrderItem> is fully populated
                order.getItems().forEach(item -> {
                    log.info("      - Product: {} x{} @ €{}",
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPricePerUnit()
                    );
                });

                // ✅ Nested PaymentInfo object is populated
                log.info("   Payment: {} (Paid: {})",
                    order.getPayment().getMethod(),
                    order.getPayment().getPaid()
                );
            })
            .subscribe();
    }

    // ============================================================
    // 📊 SUMMARY: What gets auto-converted?
    // ============================================================

    /*
     * ✅ Primitive types: String, Integer, Double, Boolean, etc.
     * ✅ Date/Time types: LocalDateTime, LocalDate, Instant, etc.
     * ✅ Collections: List<T>, Set<T>, Map<K,V>
     * ✅ Enums: Automatically converted from/to strings
     * ✅ Nested objects: Recursive conversion of complex types
     * ✅ Arrays: String[], int[], etc.
     * ✅ @OneirosEncrypted fields: Automatically decrypted after conversion
     *
     * 🔧 How it works:
     * 1. SurrealDB returns JSON via WebSocket
     * 2. Jackson ObjectMapper converts JSON → Java objects
     * 3. @OneirosEncrypted fields are decrypted transparently
     * 4. You get fully populated Java objects!
     *
     * 💡 No manual mapping needed - it just works! 🎉
     *
     * 🚀 NEW FEATURES:
     * Check out AdvancedFeaturesDemo.java for:
     * - Graph & Relate API (fluent graph edge creation)
     * - Auto-Migration (automatic schema generation)
     * - Versioning (time-travel/history tracking)
     */

    public static void main(String[] args) {
        log.info("🚀 Oneiros Auto-Conversion Examples");
        log.info("All examples show automatic JSON → Java object conversion");
        log.info("See method implementations above for details");
        log.info("");
        log.info("📚 For advanced features, see:");
        log.info("   - AdvancedFeaturesDemo.java");
        log.info("   - ADVANCED_FEATURES.md");
    }
}
