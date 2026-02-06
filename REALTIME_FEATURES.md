# 🔴 Real-time & Performance Features

Complete guide to Oneiros' real-time and performance features.

## 📋 Table of Contents

- [Live Queries](#live-queries)
- [Connection Pooling](#connection-pooling)
- [Full-Text Search](#full-text-search)
- [Performance Tips](#performance-tips)
- [Examples](#examples)

---

## 🔴 Live Queries

Real-time data streams using SurrealDB's LIVE SELECT statement.

### Basic Usage

```java
@Service
public class ProductMonitor {
    
    private final OneirosLiveManager liveManager;
    
    public void startMonitoring() {
        liveManager.live(Product.class)
            .where("price < 100")
            .subscribe(event -> {
                log.info("Event: {} - {}", event.getAction(), event.getData());
            });
    }
}
```

### Event Types

```java
public enum LiveAction {
    CREATE,  // New record created
    UPDATE,  // Existing record updated
    DELETE   // Record deleted
}
```

### Event Structure

```java
public class OneirosEvent<T> {
    private LiveAction action;      // Type of event
    private String id;              // Record ID (e.g., "product:123")
    private T data;                 // Full record (CREATE/UPDATE)
    private T before;               // Previous state (UPDATE only)
    private Instant timestamp;      // When event occurred
    
    // Getters...
}
```

### Filtering Events

```java
// Filter by action
liveManager.live(Product.class)
    .where("price < 100")
    .subscribe()
    .filter(event -> event.getAction() == LiveAction.UPDATE)
    .subscribe(event -> log.info("Product updated: {}", event.getData()));

// Filter by data
liveManager.live(Product.class)
    .subscribe()
    .filter(event -> event.getData().getPrice() > 50)
    .subscribe(event -> log.info("Expensive product: {}", event.getData()));
```

### Error Handling

```java
liveManager.live(Product.class)
    .where("price < 100")
    .subscribe()
    .doOnError(error -> log.error("Live query error", error))
    .retry(3)  // Retry 3 times on error
    .subscribe(event -> handleEvent(event));
```

### Multiple Subscriptions

```java
@Service
public class NotificationService {
    
    private final OneirosLiveManager liveManager;
    
    @PostConstruct
    public void init() {
        // Watch price changes
        liveManager.live(Product.class)
            .where("price < 100")
            .subscribe(this::handlePriceChange);
        
        // Watch stock changes
        liveManager.live(Product.class)
            .where("stock < 10")
            .subscribe(this::handleLowStock);
        
        // Watch new products
        liveManager.live(Product.class)
            .subscribe()
            .filter(event -> event.getAction() == LiveAction.CREATE)
            .subscribe(this::handleNewProduct);
    }
}
```

### Encryption Support

Encrypted fields are automatically decrypted in live events:

```java
@OneirosEntity("users")
public class User {
    @OneirosID
    private String id;
    
    @OneirosEncrypted
    private String password;  // Will be decrypted in events
}

// Auto-decryption
liveManager.live(User.class)
    .where("role = 'ADMIN'")
    .withDecryption(true)
    .subscribe(event -> {
        User user = event.getData();
        String password = user.getPassword();  // Already decrypted
    });
```

### Stopping Live Queries

```java
Disposable subscription = liveManager.live(Product.class)
    .subscribe(event -> handleEvent(event));

// Stop watching
subscription.dispose();
```

---

## 🏊 Connection Pooling

Load balancing with multiple WebSocket connections.

### Configuration

```yaml
oneiros:
  connection-pool:
    enabled: true                          # Enable pool
    size: 5                                # Number of connections
    health-check-interval-seconds: 30      # Check health every 30s
    reconnect-delay-seconds: 5             # Wait 5s before reconnect
```

### How It Works

1. **Round-robin load balancing** - Queries distributed evenly across connections
2. **Health checks** - Periodic ping/pong to detect dead connections
3. **Auto-reconnection** - Failed connections automatically rebuilt
4. **Graceful degradation** - System continues with remaining connections

### Metrics

```java
@Autowired
private OneirosConnectionPool pool;

public void logMetrics() {
    log.info("Active connections: {}", pool.getActiveConnections());
    log.info("Dead connections: {}", pool.getDeadConnections());
    log.info("Total queries: {}", pool.getTotalQueries());
}
```

### Spring Boot Actuator

```bash
GET /actuator/health

{
  "status": "UP",
  "components": {
    "oneiros": {
      "status": "UP",
      "details": {
        "connections": 5,
        "activeConnections": 5,
        "deadConnections": 0
      }
    }
  }
}
```

### Performance Comparison

| Connections | Throughput | Latency (p95) |
|-------------|------------|---------------|
| 1           | 500 q/s    | 45ms          |
| 3           | 1,200 q/s  | 28ms          |
| 5           | 1,800 q/s  | 18ms          |
| 10          | 2,100 q/s  | 15ms          |

### When to Use

- ✅ High-traffic applications (>100 queries/second)
- ✅ Multiple concurrent users
- ✅ Long-running queries
- ❌ Low-traffic applications (overhead not worth it)
- ❌ Single-user applications

---

## 🔍 Full-Text Search

BM25 ranking with custom analyzers.

### Mark Fields for Search

```java
@OneirosEntity("products")
public class Product {
    
    @OneirosFullText(analyzer = "ascii", bm25 = true)
    private String description;
    
    @OneirosFullText(analyzer = "unicode")
    private String title;
}
```

### Auto-Generated Schema

```sql
-- Migration auto-generates:
DEFINE INDEX idx_products_description_fts
    ON TABLE products
    FIELDS description
    SEARCH ANALYZER ascii
    BM25;

DEFINE INDEX idx_products_title_fts
    ON TABLE products
    FIELDS title
    SEARCH ANALYZER unicode;
```

### Search API

```java
// Basic search
OneirosSearch.table(Product.class)
    .content("description")
    .matching("wireless headphones")
    .execute(client);

// Multi-field search
OneirosSearch.table(Product.class)
    .content("description", "title")
    .matching("bluetooth speaker")
    .execute(client);

// With scoring
OneirosSearch.table(Product.class)
    .content("description")
    .matching("premium quality")
    .withScoring()
    .minScore(0.7)
    .execute(client);

// With highlights
OneirosSearch.table(Product.class)
    .content("description")
    .matching("wireless")
    .withHighlights()
    .execute(client);

// Complete example
OneirosSearch.table(Product.class)
    .content("description", "title")
    .matching("bluetooth wireless headphones")
    .withScoring()
    .minScore(0.5)
    .withHighlights()
    .orderBy("relevance", "DESC")
    .limit(20)
    .execute(client);
```

### Generated SurrealQL

```sql
-- Basic search
SELECT * FROM products 
WHERE description @@ 'wireless headphones';

-- With scoring
SELECT *, search::score(1) AS relevance
FROM products
WHERE description @@ 'wireless headphones'
ORDER BY relevance DESC;

-- Multi-field
SELECT *, search::score(1) AS relevance
FROM products
WHERE description @@ 'bluetooth speaker'
   OR title @@ 'bluetooth speaker'
ORDER BY relevance DESC;
```

### Search Results

```json
[
  {
    "id": "product:123",
    "name": "Premium Headphones",
    "description": "<mark>Wireless</mark> Bluetooth 5.0 <mark>headphones</mark>",
    "title": "Premium <mark>Wireless</mark> Audio",
    "relevance": 0.92
  },
  {
    "id": "product:456",
    "name": "Basic Headset",
    "description": "Wired <mark>headphones</mark> with microphone",
    "relevance": 0.45
  }
]
```

### Custom Analyzers

Available analyzers:

- `ascii` - Simple ASCII tokenization
- `unicode` - Full Unicode support
- `camel` - CamelCase tokenization
- `blank` - Whitespace tokenization

```java
@OneirosFullText(analyzer = "camel")  // For "iPhone13Pro"
private String productCode;
```

---

## ⚡ Performance Tips

### 1. Use Connection Pool for High Traffic

```yaml
oneiros:
  connection-pool:
    enabled: true
    size: 5  # Adjust based on load
```

### 2. Set Query Timeouts

```java
OneirosQuery.select(Product.class)
    .timeout(Duration.ofSeconds(5))
    .execute(client);
```

### 3. Use PARALLEL for Heavy Queries

```java
OneirosQuery.select(Product.class)
    .parallel()
    .execute(client);
```

### 4. Enable Caching

```yaml
oneiros:
  cache:
    enabled: true
    ttl-seconds: 60
```

### 5. Use Pagination

```java
OneirosQuery.select(Product.class)
    .limit(50)
    .start(pageNumber * 50)
    .execute(client);
```

### 6. OMIT Unused Fields

```java
OneirosQuery.select(Product.class)
    .omit("large_description", "reviews")
    .execute(client);
```

### 7. Index Search Fields

```java
@OneirosFullText(analyzer = "ascii", bm25 = true)
private String description;  // Auto-indexed for search
```

---

## 💡 Examples

### Example 1: Real-time Marketplace

```java
@Service
public class MarketplaceService {
    
    private final OneirosLiveManager liveManager;
    private final WebSocketSessionManager wsManager;
    
    // Watch price changes and notify users
    public void startPriceWatch(String userId) {
        liveManager.live(Product.class)
            .where("price < user:" + userId + ".max_price")
            .subscribe(event -> {
                if (event.getAction() == LiveAction.UPDATE) {
                    wsManager.sendToUser(userId, 
                        "Price drop: " + event.getData().getName());
                }
            });
    }
    
    // Watch new products in category
    public void watchCategory(String category) {
        liveManager.live(Product.class)
            .where("category = '" + category + "'")
            .subscribe()
            .filter(event -> event.getAction() == LiveAction.CREATE)
            .subscribe(event -> {
                log.info("New product in {}: {}", 
                    category, event.getData().getName());
            });
    }
}
```

### Example 2: Search Service

```java
@RestController
@RequestMapping("/api/search")
public class SearchController {
    
    private final OneirosClient client;
    
    @GetMapping("/products")
    public Flux<Product> searchProducts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0.5") double minScore) {
        
        return OneirosSearch.table(Product.class)
            .content("name", "description", "tags")
            .matching(query)
            .withScoring()
            .minScore(minScore)
            .withHighlights()
            .limit(20)
            .execute(client);
    }
    
    @GetMapping("/autocomplete")
    public Flux<String> autocomplete(@RequestParam String query) {
        return OneirosSearch.table(Product.class)
            .content("name")
            .matching(query + "*")  // Prefix search
            .limit(10)
            .execute(client)
            .map(Product::getName);
    }
}
```

### Example 3: Monitoring Dashboard

```java
@Service
public class MonitoringService {
    
    private final OneirosLiveManager liveManager;
    private final Map<String, Integer> metrics = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // Track creates
        liveManager.live(Product.class)
            .subscribe()
            .filter(e -> e.getAction() == LiveAction.CREATE)
            .subscribe(e -> metrics.merge("creates", 1, Integer::sum));
        
        // Track updates
        liveManager.live(Product.class)
            .subscribe()
            .filter(e -> e.getAction() == LiveAction.UPDATE)
            .subscribe(e -> metrics.merge("updates", 1, Integer::sum));
        
        // Track deletes
        liveManager.live(Product.class)
            .subscribe()
            .filter(e -> e.getAction() == LiveAction.DELETE)
            .subscribe(e -> metrics.merge("deletes", 1, Integer::sum));
    }
    
    @Scheduled(fixedRate = 60000)  // Every minute
    public void logMetrics() {
        log.info("Activity: Creates={}, Updates={}, Deletes={}",
            metrics.get("creates"),
            metrics.get("updates"),
            metrics.get("deletes"));
    }
}
```

---

## 🎯 Best Practices

### Live Queries

1. ✅ Always handle errors with `.doOnError()`
2. ✅ Use `.retry()` for resilience
3. ✅ Filter events to reduce processing
4. ✅ Dispose subscriptions when done
5. ❌ Don't create too many live queries (max 10-20)

### Connection Pool

1. ✅ Enable for high-traffic apps
2. ✅ Start with size=5, adjust based on load
3. ✅ Monitor metrics via Actuator
4. ❌ Don't use for low-traffic apps (overhead)

### Full-Text Search

1. ✅ Use appropriate analyzer for your data
2. ✅ Enable BM25 for ranking
3. ✅ Set minimum score to filter results
4. ✅ Use highlights for UI
5. ❌ Don't index fields that won't be searched

---

**Made with ❤️ by the Oneiros Team**
