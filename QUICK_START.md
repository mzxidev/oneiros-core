# 🚀 Quick Reference: New Features in Oneiros v0.2.0

## 3 Major New Features

### 1️⃣ **Graph & Relate API** - Simplified Relationships

**Old Way (Manual SurrealQL):**
```java
String sql = "RELATE user:alice->purchased->product:laptop SET price = 999.99";
client.query(sql);
```

**New Way (Fluent API):**
```java
graph.from(user)
    .to(product)
    .via("purchased")
    .with("price", 999.99)
    .with("quantity", 1)
    .execute();
```

**With Encryption:**
```java
Purchase purchase = new Purchase();
purchase.setPaymentToken("secret");  // @OneirosEncrypted

graph.from("user:alice")
    .to("product:laptop")
    .via("purchased")
    .withEntity(purchase)  // Auto-encrypts sensitive fields
    .execute(Purchase.class);
```

---

### 2️⃣ **Auto-Migration Engine** - Schema from Annotations

**Add Annotations:**
```java
@OneirosTable(isStrict = true)
@OneirosVersioned(maxVersions = 10)
@OneirosEntity("users")
public class User {
    @OneirosField(
        type = "string",
        unique = true,
        index = true,
        assertion = "$value.length() > 5"
    )
    private String email;
    
    @OneirosEncrypted
    private String password;
}
```

**Enable in application.yml:**
```yaml
oneiros:
  migration:
    enabled: true
    auto-create-schema: true
    base-package: "com.example.domain"
```

**Automatically Generates:**
```sql
DEFINE TABLE users SCHEMAFULL;
DEFINE FIELD email ON users TYPE string ASSERT $value.length() > 5;
DEFINE INDEX idx_user_email ON users FIELDS email UNIQUE;
DEFINE TABLE user_history SCHEMALESS;
```

---

### 3️⃣ **Automatic Versioning** - Built-in Time-Travel

**Enable:**
```java
@OneirosVersioned(
    historyTable = "user_history",
    maxVersions = 10,
    retentionDays = 90
)
public class User { ... }
```

**Every Update Creates History:**
```java
user.setAge(26);
userRepo.save(user);  
// ✅ Old version automatically saved to user_history
```

**Query History:**
```java
client.query(
    "SELECT * FROM user_history WHERE record_id = $id ORDER BY timestamp DESC",
    Map.of("id", "user:alice"),
    Map.class
);
```

**Result:**
```json
[{
  "record_id": "user:alice",
  "event_type": "UPDATE",
  "timestamp": "2024-02-05T10:30:00Z",
  "data": {
    "before": { "age": 25, "name": "Alice" },
    "after": { "age": 26, "name": "Alice" }
  }
}]
```

---

## 🎯 Common Use Cases

### Create a Relationship with Data
```java
graph.from("user:alice")
    .to("product:laptop")
    .via("purchased")
    .with("price", 999.99)
    .with("timestamp", Instant.now())
    .with("paymentMethod", "credit_card")
    .execute();
```

### Query Relationships (Bidirectional)
```java
// Get what user purchased
client.query("SELECT ->purchased->product FROM user:alice");

// Get who purchased a product
client.query("SELECT <-purchased<-user FROM product:laptop");
```

### Complex Transaction with All Features
```java
new BeginStatement()
    .add(new LetStatement("$user", "user:alice"))
    .add(new IfStatement("$user.balance < 100")
        .then(new ThrowStatement("Insufficient funds")))
    .add(new UpdateStatement("$user")
        .set("balance", "balance - 100"))
    .add(graph.from("$user")
        .to("product:laptop")
        .via("purchased")
        .with("price", 100)
        .toStatement())
    .add(new CommitStatement())
    .execute(client);
```

---

## 📋 New Annotations Reference

| Annotation | Purpose | Example |
|------------|---------|---------|
| `@OneirosTable` | Table config | `@OneirosTable(isStrict = true)` |
| `@OneirosField` | Field config | `@OneirosField(type = "string", unique = true)` |
| `@OneirosRelation` | Define relations | `@OneirosRelation(target = User.class)` |
| `@OneirosVersioned` | Enable versioning | `@OneirosVersioned(maxVersions = 10)` |

---

## ⚙️ Configuration Quick Setup

```yaml
oneiros:
  # Basic connection
  url: ws://localhost:8000/rpc
  username: root
  password: root
  namespace: test
  database: test
  
  # NEW: Migration
  migration:
    enabled: true
    auto-create-schema: true
    base-package: "com.example"
  
  # NEW: Versioning
  versioning:
    enabled: true
    retention-days: 90
  
  # NEW: Graph API
  graph:
    encryption-on-edges: true
```

---

## 🔥 Complete Example

```java
@RestController
@RequestMapping("/api")
public class ShopController {
    private final OneirosGraph graph;
    private final UserRepository userRepo;
    
    @PostMapping("/purchase")
    public Mono<Map<String, Object>> purchase(
        @RequestParam String userId,
        @RequestParam String productId,
        @RequestParam double price
    ) {
        return new BeginStatement()
            // Check user exists
            .add(new LetStatement("$user", 
                new SelectStatement("users")
                    .where("id = " + userId)
            ))
            .add(new IfStatement("!$user")
                .then(new ThrowStatement("User not found")))
            
            // Check balance
            .add(new IfStatement("$user.balance < " + price)
                .then(new ThrowStatement("Insufficient funds")))
            
            // Deduct balance
            .add(new UpdateStatement(userId)
                .set("balance", "balance - " + price))
            
            // Create purchase relation
            .add(graph.from(userId)
                .to(productId)
                .via("purchased")
                .with("price", price)
                .with("timestamp", Instant.now())
                .toStatement())
            
            // Return success
            .add(new ReturnStatement(Map.of(
                "success", true,
                "message", "Purchase completed"
            )))
            
            .add(new CommitStatement())
            .execute(client);
    }
    
    // Query user's purchase history (uses version history)
    @GetMapping("/users/{id}/history")
    public Flux<Map<String, Object>> getHistory(@PathVariable String id) {
        return client.query(
            "SELECT * FROM user_history WHERE record_id = $id ORDER BY timestamp DESC",
            Map.of("id", "user:" + id),
            Map.class
        );
    }
}
```

---

## 📚 Full Documentation

- **Complete Guide:** [README_COMPLETE.md](README_COMPLETE.md)
- **Advanced Features:** [ADVANCED_FEATURES.md](ADVANCED_FEATURES.md)
- **Examples:** See `src/main/java/io/oneiros/test/`

---

## ✅ Migration Checklist

To upgrade from v0.1.x to v0.2.0:

- [ ] Add JitPack repository to build.gradle
- [ ] Update dependency to `v0.2.0`
- [ ] Add migration config to application.yml
- [ ] Add `@OneirosTable` to entity classes
- [ ] Add `@OneirosField` to important fields
- [ ] Enable versioning with `@OneirosVersioned` where needed
- [ ] Replace manual RELATE statements with Graph API
- [ ] Test schema generation in dry-run mode first
- [ ] Deploy and verify migrations

---

**Questions? See [README_COMPLETE.md](README_COMPLETE.md) for full documentation!**
