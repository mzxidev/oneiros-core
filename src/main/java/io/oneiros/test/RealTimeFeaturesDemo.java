package io.oneiros.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.oneiros.annotation.OneirosEntity;
import io.oneiros.annotation.OneirosFullText;
import io.oneiros.annotation.OneirosID;
import io.oneiros.client.OneirosWebsocketClient;
import io.oneiros.config.OneirosProperties;
import io.oneiros.live.OneirosLiveManager;
import io.oneiros.pool.OneirosConnectionPool;
import io.oneiros.search.OneirosSearch;
import io.oneiros.security.CryptoService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Demo for Real-time & Performance Features:
 * - Live Queries (LIVE SELECT)
 * - Connection Pooling with Load Balancing
 * - Full-Text Search API
 */
@Slf4j
public class RealTimeFeaturesDemo {

    /**
     * Product entity with full-text search on description.
     */
    @Data
    @OneirosEntity("products")
    public static class Product {
        @OneirosID
        private String id;
        private String name;

        @OneirosFullText(analyzer = "ascii", bm25 = true)
        private String description;

        private Double price;
        private String category;
    }

    public static void main(String[] args) {
        log.info("🚀 Oneiros Real-time & Performance Features Demo");
        log.info("═══════════════════════════════════════════════════");
        log.info("");
        log.info("📋 DEMO MODE: Showing feature usage (no database required)");
        log.info("─────────────────────────────────────────────────");
        log.info("");

        showLiveQueryExample();
        showConnectionPoolExample();
        showFullTextSearchExample();

        log.info("");
        log.info("═══════════════════════════════════════════════════");
        log.info("✅ Demo completed successfully!");
        log.info("");
        log.info("📚 To run with actual database:");
        log.info("   1. Start SurrealDB: surreal start --user root --pass root");
        log.info("   2. Enable features in application.yml:");
        log.info("      oneiros:");
        log.info("        connection-pool:");
        log.info("          enabled: true");
        log.info("          size: 5");
        log.info("   3. Uncomment runWithDatabase() in main()");
        log.info("   4. Run again");
        log.info("");
        log.info("📖 For full documentation, see:");
        log.info("   - ADVANCED_FEATURES.md");
        log.info("   - README.md");
        log.info("═══════════════════════════════════════════════════");
    }

    /**
     * Demo: Live Query API for real-time updates.
     */
    private static void showLiveQueryExample() {
        log.info("1️⃣ LIVE QUERY API - Real-time data streams:");
        log.info("");
        log.info("   // Subscribe to live updates");
        log.info("   liveManager.live(Product.class)");
        log.info("       .where(\"price < 100\")");
        log.info("       .subscribe(event -> {");
        log.info("           switch(event.getAction()) {");
        log.info("               case CREATE -> log.info(\"New product: {}\", event.getData());");
        log.info("               case UPDATE -> log.info(\"Updated: {}\", event.getData());");
        log.info("               case DELETE -> log.info(\"Deleted: {}\", event.getId());");
        log.info("           }");
        log.info("       });");
        log.info("");
        log.info("   // With encryption support");
        log.info("   liveManager.live(User.class)");
        log.info("       .where(\"role = 'ADMIN'\")");
        log.info("       .withDecryption(true)");
        log.info("       .subscribe(event -> {");
        log.info("           // @OneirosEncrypted fields are auto-decrypted");
        log.info("           User user = event.getData();");
        log.info("           log.info(\"Admin action: {}\", user.getName());");
        log.info("       });");
        log.info("");
        log.info("   // Generated SurrealQL:");
        log.info("   LIVE SELECT * FROM products WHERE price < 100;");
        log.info("");
        log.info("─────────────────────────────────────────────────");
    }

    /**
     * Demo: Connection Pool for load balancing.
     */
    private static void showConnectionPoolExample() {
        log.info("2️⃣ CONNECTION POOL - Load balancing & resilience:");
        log.info("");
        log.info("   Configuration (application.yml):");
        log.info("   oneiros:");
        log.info("     connection-pool:");
        log.info("       enabled: true");
        log.info("       size: 5                          # 5 WebSocket connections");
        log.info("       health-check-interval-seconds: 30 # Check health every 30s");
        log.info("       reconnect-delay-seconds: 5       # Wait 5s before reconnect");
        log.info("");
        log.info("   Features:");
        log.info("   ✅ Round-robin load balancing across connections");
        log.info("   ✅ Automatic dead connection detection & removal");
        log.info("   ✅ Automatic reconnection with exponential backoff");
        log.info("   ✅ Health metrics via Spring Boot Actuator");
        log.info("");
        log.info("   Usage (transparent - no code changes needed):");
        log.info("   @Autowired");
        log.info("   private OneirosClient client;  // Auto-wired as pool if enabled");
        log.info("");
        log.info("   client.query(\"SELECT * FROM products\", Product.class)");
        log.info("       .subscribe();  // Automatically uses next available connection");
        log.info("");
        log.info("─────────────────────────────────────────────────");
    }

    /**
     * Demo: Full-Text Search API.
     */
    private static void showFullTextSearchExample() {
        log.info("3️⃣ FULL-TEXT SEARCH API - Powerful search capabilities:");
        log.info("");
        log.info("   // Mark field for full-text indexing");
        log.info("   @OneirosEntity(\"products\")");
        log.info("   public class Product {");
        log.info("       @OneirosFullText(analyzer = \"ascii\", bm25 = true)");
        log.info("       private String description;");
        log.info("   }");
        log.info("");
        log.info("   // Auto-generated DEFINE statements:");
        log.info("   DEFINE INDEX idx_products_description_fts");
        log.info("       ON TABLE products");
        log.info("       FIELDS description");
        log.info("       SEARCH ANALYZER ascii");
        log.info("       BM25;");
        log.info("");
        log.info("   // Fluent Search API:");
        log.info("   OneirosSearch.table(Product.class)");
        log.info("       .content(\"description\")");
        log.info("       .matching(\"wireless bluetooth headphones\")");
        log.info("       .withScoring()");
        log.info("       .minScore(0.7)");
        log.info("       .limit(20)");
        log.info("       .execute(client);");
        log.info("");
        log.info("   // Generated SurrealQL:");
        log.info("   SELECT *, search::score(1) AS relevance");
        log.info("       FROM products");
        log.info("       WHERE description @@ 'wireless bluetooth headphones'");
        log.info("       ORDER BY relevance DESC");
        log.info("       LIMIT 20;");
        log.info("");
        log.info("   // With highlights:");
        log.info("   OneirosSearch.table(Product.class)");
        log.info("       .content(\"description\")");
        log.info("       .matching(\"wireless\")");
        log.info("       .withHighlights()");
        log.info("       .execute(client);");
        log.info("");
        log.info("   // Returns:");
        log.info("   {");
        log.info("     \"id\": \"product:123\",");
        log.info("     \"name\": \"Headphones\",");
        log.info("     \"description\": \"<mark>Wireless</mark> Bluetooth 5.0 headphones\",");
        log.info("     \"relevance\": 0.92");
        log.info("   }");
        log.info("");
        log.info("─────────────────────────────────────────────────");
    }

    /**
     * Run with actual database connection (commented out by default).
     */
    @SuppressWarnings("unused")
    private static void runWithDatabase() {
        log.info("🔌 Connecting to SurrealDB...");

        // Setup
        OneirosProperties props = new OneirosProperties();
        props.setUrl("ws://127.0.0.1:8000/rpc");
        props.setUsername("root");
        props.setPassword("root");
        props.setNamespace("test");
        props.setDatabase("test");

        ObjectMapper mapper = new ObjectMapper();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(
            CircuitBreakerConfig.ofDefaults()
        );
        CircuitBreaker breaker = registry.circuitBreaker("oneiros");

        // Create connection pool
        OneirosConnectionPool pool = new OneirosConnectionPool(props, mapper, breaker, 5);
        pool.connect().block();

        // Create live manager
        CryptoService crypto = new CryptoService(props);
        OneirosLiveManager liveManager = new OneirosLiveManager(pool, mapper, crypto);

        // Example: Subscribe to product updates
        log.info("📡 Subscribing to live product updates...");
        liveManager.live(Product.class)
            .where("price < 100")
            .subscribe()
            .subscribe(
                event -> log.info("Event: {} - {}", event.getAction(), event.getData()),
                error -> log.error("Error in live stream", error),
                () -> log.info("Live stream completed")
            );

        // Keep alive
        try {
            log.info("✅ Live query active. Press Ctrl+C to stop.");
            Thread.sleep(60000); // Wait 60 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
