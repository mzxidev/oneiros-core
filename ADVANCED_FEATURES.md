# 🚀 Oneiros Advanced Features Documentation

This document covers the three new core modules added to Oneiros:

1. **Graph & Relate API** - Fluent API for creating graph relations
2. **Auto-Migration Engine** - Automatic schema generation from annotations
3. **Versioning System** - Automatic time-travel/history tracking

---

## 1️⃣ Graph & Relate API

The Graph API provides a fluent interface for creating relations (edges) between records in SurrealDB.

### Basic Usage

```java
@Autowired
private OneirosGraph graph;

// Create a relation between user and product
graph.from("user:alice")
    .to("product:laptop")
    .via("purchased")
    .with("price", 999.99)
    .with("date", LocalDateTime.now())
    .execute();
```

### Using Entity Objects

```java
// From entity object (extracts @OneirosID automatically)
User user = userRepo.findById("user:alice").block();
Product product = productRepo.findById("product:laptop").block();

graph.from(user)
    .to(product)
    .via("purchased")
    .withData(Map.of("price", 999.99))
    .execute(Purchase.class)
    .subscribe(purchase -> {
        System.out.println("Created: " + purchase.getId());
    });
```

### Edge Data with Encryption

```java
// Edge entity with encrypted fields
@Data
@OneirosEntity("purchased")
public class Purchase {
    @OneirosID
    private String id;
    
    @OneirosEncrypted
    private String paymentToken;  // Will be encrypted automatically
    
    private Double price;
}

// Create relation with automatic encryption
Purchase data = new Purchase();
data.setPrice(999.99);
data.setPaymentToken("tok_secret123");  // Encrypted before sending

graph.from("user:alice")
    .to("product:laptop")
    .via("purchased")
    .withEntity(data)  // Auto-encrypts @OneirosEncrypted fields
    .execute();
```

### Return Options

```java
// Return the created edge record (default)
graph.from(user).to(product).via("purchased")
    .returnAfter()
    .execute(Purchase.class);

// Return the diff of changes
graph.from(user).to(product).via("purchased")
    .returnDiff()
    .execute(Map.class);

// Don't return anything
graph.from(user).to(product).via("purchased")
    .execute();
```

### Advanced Options

```java
graph.from("user:alice")
    .to("product:laptop")
    .via("purchased")
    .with("price", 999.99)
    .withoutEncryption()  // Disable encryption for this operation
    .timeout("10s")       // Set query timeout
    .returnAfter()
    .execute();
```

### Generating SQL

```java
// Get the generated SurrealQL without executing
String sql = graph.from("user:alice")
    .to("product:laptop")
    .via("purchased")
    .with("price", 999.99)
    .toSql();

System.out.println(sql);
// Output: RELATE user:alice->purchased->product:laptop 
//         CONTENT {"price":999.99} RETURN AFTER
```

### Method Reference

| Method | Description |
|--------|-------------|
| `from(String)` | Set source record (IN side) |
| `from(Object)` | Set source from entity object |
| `to(String)` | Set target record (OUT side) |
| `to(Object)` | Set target from entity object |
| `via(String)` | Set edge table name |
| `with(String, Object)` | Add single field to edge |
| `withData(Map)` | Add multiple fields to edge |
| `withEntity(Object)` | Set data from entity object |
| `withoutEncryption()` | Disable automatic encryption |
| `returnBefore()` | Return record before creation |
| `returnAfter()` | Return record after creation (default) |
| `returnDiff()` | Return changeset diff |
| `timeout(String)` | Set operation timeout |
| `execute()` | Execute without returning result |
| `execute(Class<T>)` | Execute and return typed result |
| `toSql()` | Generate SQL without executing |

---

## 2️⃣ Auto-Migration Engine

The Migration Engine automatically generates SurrealDB schema definitions from your entity classes.

### New Annotations

#### `@OneirosTable`

Define table-level configuration.

```java
@OneirosTable(
    isStrict = true,           // SCHEMAFULL vs SCHEMALESS
    comment = "User accounts",
    changeFeed = "3d",         // Enable change feed for 3 days
    type = "NORMAL"            // "NORMAL" or "RELATION"
)
@OneirosEntity("users")
public class User { ... }
```

**Generated SurrealQL:**
```sql
DEFINE TABLE users TYPE NORMAL SCHEMAFULL 
    COMMENT 'User accounts' 
    CHANGEFEED 3d
```

#### `@OneirosField`

Define field-level configuration.

```java
@OneirosField(
    type = "string",                    // SurrealDB type
    unique = true,                      // Create UNIQUE index
    index = true,                       // Create regular index
    indexName = "idx_user_email",       // Custom index name
    defaultValue = "'unknown'",         // Default value
    readonly = true,                    // Field is readonly
    assertion = "$value.length() > 5",  // Validation assertion
    comment = "User email address"
)
private String email;
```

**Generated SurrealQL:**
```sql
DEFINE FIELD email ON TABLE users 
    TYPE string 
    DEFAULT 'unknown'
    READONLY
    ASSERT $value.length() > 5
    COMMENT 'User email address';

DEFINE INDEX idx_user_email ON TABLE users FIELDS email UNIQUE;
```

#### `@OneirosRelation`

Define graph relations with type validation.

```java
@OneirosRelation(
    target = User.class,
    type = RelationType.ONE_TO_MANY,
    bidirectional = true,
    onDelete = "CASCADE"
)
@OneirosField(type = "array<record<users>>")
private List<String> friends;
```

**Generated SurrealQL:**
```sql
DEFINE FIELD friends ON TABLE users 
    TYPE array<record<users>>
    ASSERT array::all($value, |$v| $v.type() = 'users')
```

### Configuration

Enable/disable auto-migration in `application.yml`:

```yaml
oneiros:
  migration:
    enabled: true                  # Enable auto-migration
    base-package: io.oneiros      # Base package to scan
    dry-run: false                # If true, only logs SQL
```

### Manual Migration

```java
@Autowired
private OneirosMigrationEngine migrationEngine;

// Execute migration manually
migrationEngine.migrate()
    .doOnSuccess(v -> log.info("Migration complete"))
    .subscribe();
```

### Type Inference

The engine automatically infers SurrealDB types from Java types:

| Java Type | SurrealDB Type |
|-----------|----------------|
| `String` | `string` |
| `Integer`, `int`, `Long`, `long` | `int` |
| `Double`, `double`, `Float`, `float` | `float` |
| `Boolean`, `boolean` | `bool` |
| `LocalDateTime`, `LocalDate`, `Instant` | `datetime` |
| `UUID` | `uuid` |
| `Duration` | `duration` |
| `List<T>`, `Set<T>` | `array<T>` |
| `Map<K,V>` | `object` |

---

## 3️⃣ Versioning System

Automatic history tracking for entity changes (time-travel feature).

### Enable Versioning

```java
@OneirosEntity("users")
@OneirosVersioned(
    historyTable = "user_history",  // History table name (optional)
    maxVersions = 10,                // Limit versions per record
    includeMetadata = true,          // Include user/session info
    fullSnapshots = true             // Store full records (vs diffs)
)
public class User {
    @OneirosID
    private String id;
    
    private String name;
    private Integer age;
}
```

### Generated Schema

The engine automatically creates:

1. **History Table**:
```sql
DEFINE TABLE user_history SCHEMALESS
```

2. **Event Trigger**:
```sql
DEFINE EVENT users_history_event ON TABLE users 
    WHEN $event = 'UPDATE' OR $event = 'DELETE'
    THEN {
        CREATE user_history SET 
            record_id = $before.id,
            event_type = $event,
            timestamp = time::now(),
            metadata = { user: $auth.id, session: $session },
            data = $before
    }
```

### Usage

```java
// Create user
User user = new User();
user.setName("Alice");
user.setAge(25);
userRepo.save(user).subscribe();

// Update user - old version saved automatically
user.setAge(26);
userRepo.save(user).subscribe();

// Query history
String sql = "SELECT * FROM user_history WHERE record_id = " + user.getId() 
           + " ORDER BY timestamp DESC";
           
client.query(sql, Map.class)
    .collectList()
    .subscribe(history -> {
        history.forEach(version -> {
            System.out.println("Event: " + version.get("event_type"));
            System.out.println("Timestamp: " + version.get("timestamp"));
            System.out.println("Old data: " + version.get("data"));
        });
    });
```

### History Record Structure

Each history record contains:

```json
{
  "id": "user_history:abc123",
  "record_id": "user:alice",
  "event_type": "UPDATE",
  "timestamp": "2024-01-15T10:30:00Z",
  "metadata": {
    "user": "user:admin",
    "session": {...}
  },
  "data": {
    "id": "user:alice",
    "name": "Alice",
    "age": 25
  }
}
```

### Time-Travel Queries

```java
// Get user state at specific time
String sql = "SELECT * FROM user:alice VERSION d'2024-01-15T10:00:00Z'";
client.query(sql, User.class).subscribe(...);

// Get all changes in a time range
sql = """
    SELECT * FROM user_history 
    WHERE record_id = user:alice 
    AND timestamp BETWEEN d'2024-01-01' AND d'2024-01-31'
    ORDER BY timestamp DESC
    """;
client.query(sql, Map.class).collectList().subscribe(...);
```

---

## 🔐 Security Integration

All three modules integrate seamlessly with `@OneirosEncrypted`:

### Encrypted Fields in Relations

```java
@OneirosEntity("transactions")
public class Transaction {
    @OneirosID
    private String id;
    
    @OneirosEncrypted
    private String creditCard;  // Encrypted in graph edges
    
    private Double amount;
}

// Encryption happens automatically
graph.from("user:alice")
    .to("merchant:shop")
    .via("transaction")
    .withEntity(transaction)  // creditCard encrypted before sending
    .execute();
```

### Encrypted Fields in History

```java
@OneirosVersioned
@OneirosEntity("users")
public class User {
    @OneirosEncrypted
    private String ssn;  // Encrypted in history records too
}
```

---

## 📚 Complete Example

See `AdvancedFeaturesDemo.java` for a comprehensive example using all three modules together.

---

## 🎯 Benefits

✅ **Type-Safe**: All operations are type-safe with Java generics  
✅ **Non-Blocking**: Built on Project Reactor (Mono/Flux)  
✅ **Automatic**: Schema generation, encryption, versioning all automatic  
✅ **Flexible**: Can disable features per operation or globally  
✅ **Integrated**: Works seamlessly with existing Oneiros features  
✅ **Secure**: Encryption transparently integrated everywhere  

---

## 🔧 Configuration Summary

```yaml
oneiros:
  # Connection
  url: ws://localhost:8000/rpc
  namespace: my_namespace
  database: my_database
  username: root
  password: root
  
  # Security
  security:
    enabled: true
    key: "encryption-key-at-least-16-chars"
  
  # Migration
  migration:
    enabled: true
    base-package: io.oneiros
    dry-run: false
  
  # Cache
  cache:
    enabled: true
    ttl-seconds: 60
    max-size: 10000
```

---

## 🐛 Troubleshooting

### Migration not running?

1. Check `oneiros.migration.enabled=true`
2. Verify `base-package` includes your entities
3. Check logs for scan results

### Versioning not working?

1. Ensure `@OneirosVersioned` on entity class
2. Check that table was created (history table)
3. Verify event was created with `INFO FOR TABLE`

### Graph API errors?

1. Verify record IDs exist before creating relations
2. Check encryption key is set if using encrypted fields
3. Use `toSql()` to debug generated queries

---

## 📖 Further Reading

- [SurrealDB RELATE Documentation](https://surrealdb.com/docs/surrealql/statements/relate)
- [SurrealDB DEFINE Documentation](https://surrealdb.com/docs/surrealql/statements/define)
- [SurrealDB Events Documentation](https://surrealdb.com/docs/surrealql/statements/define/event)
