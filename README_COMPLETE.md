# 🌙 Oneiros - Complete Reactive SurrealDB Library for Java

<div align="center">

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![SurrealDB](https://img.shields.io/badge/SurrealDB-2.0+-purple.svg)](https://surrealdb.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%7C%204.x-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![JitPack](https://jitpack.io/v/mzxidev/oneiros-core.svg)](https://jitpack.io/#mzxidev/oneiros-core)

**A comprehensive, type-safe, reactive client library for SurrealDB featuring:**
- 🔥 Fluent Query Builder & Complete Statement API
- 🌐 Graph & Relate API for relationships
- 🔄 Auto-Migration Engine with schema generation
- ⏱️ Automatic Versioning & Time-Travel queries
- 🔐 Built-in AES-256-GCM encryption
- ⚡ Full reactive support with Project Reactor

[Quick Start](#-quick-start) • [Features](#-all-features) • [Documentation](#-complete-documentation) • [Examples](#-comprehensive-examples)

</div>

---

## 📋 Table of Contents

- [All Features](#-all-features)
- [Installation](#-installation)
- [Quick Start](#-quick-start)
- [Configuration](#-configuration)
- [Core APIs](#-core-apis)
  - [1. Fluent Query Builder](#1-fluent-query-builder)
  - [2. Statement API](#2-statement-api)
  - [3. Graph & Relate API](#3-graph--relate-api-new)
  - [4. Auto-Migration Engine](#4-auto-migration-engine-new)
  - [5. Automatic Versioning](#5-automatic-versioning-new)
- [Advanced Usage](#-advanced-usage)
- [Complete API Reference](#-complete-api-reference)
- [Best Practices](#-best-practices)

---

## ✨ All Features

### 🚀 **Core Capabilities**
- ✅ **Reactive & Non-Blocking** - Project Reactor (Mono/Flux) for async operations
- ✅ **Type-Safe** - Full compile-time type checking
- ✅ **Fluent Query API** - Intuitive chainable queries: `.select().where().orderBy()`
- ✅ **Complete Statement API** - All SurrealQL statements with fluent builders
- ✅ **Spring Boot Auto-Config** - Zero-config setup with `@EnableOneiros`

### 🔥 **Advanced Features (NEW)**
- 🌐 **Graph & Relate API** - Simplified graph relationship management
  ```java
  graph.from(user).to(product).via("purchased").with("price", 99.99).execute();
  ```

- 🔄 **Auto-Migration Engine** - Automatic schema generation from annotations
  ```java
  @OneirosTable(isStrict = true)
  @OneirosVersioned(maxVersions = 10)
  public class User { ... }
  // Automatically generates: DEFINE TABLE, FIELD, INDEX statements
  ```

- ⏱️ **Automatic Versioning** - Built-in time-travel and history tracking
  ```java
  @OneirosVersioned(historyTable = "user_history")
  // Every update creates history snapshot automatically
  ```

- 🔐 **Field Encryption** - Transparent AES-256-GCM encryption
  ```java
  @OneirosEncrypted
  private String password; // Auto-encrypted in DB, auto-decrypted on read
  ```

### 📊 **Complete SurrealQL Support**
- ✅ **Data Operations**: SELECT, CREATE, UPDATE, DELETE, UPSERT, INSERT
- ✅ **Graph Operations**: RELATE with bidirectional relationships
- ✅ **Control Flow**: IF/ELSE, FOR, BREAK, CONTINUE
- ✅ **Transactions**: BEGIN, COMMIT, CANCEL with fluent API
- ✅ **Variables**: LET, RETURN, THROW
- ✅ **Clauses**: WHERE, ORDER BY, LIMIT, START, FETCH, OMIT, TIMEOUT, PARALLEL
- ✅ **Advanced**: SPLIT, GROUP BY, EXPLAIN, WITH INDEX

---

## 📦 Installation

### Gradle (build.gradle)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }  // ⚠️ REQUIRED
}

dependencies {
    implementation 'com.github.mzxidev:oneiros-core:v0.2.0'
    
    // Required dependencies
    implementation 'org.springframework.boot:spring-boot-starter-webflux:3.2.0'
    implementation 'io.projectreactor:reactor-core:3.6.0'
}
```

### Maven (pom.xml)

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.mzxidev</groupId>
    <artifactId>oneiros-core</artifactId>
    <version>v0.2.0</version>
</dependency>
```

---

## 🚀 Quick Start

### 1. Configure Application

```yaml
# application.yml
oneiros:
  url: ws://localhost:8000/rpc
  username: root
  password: root
  namespace: test
  database: test
  
  # NEW: Advanced features
  migration:
    enabled: true
    auto-create-schema: true
    base-package: "com.example.domain"
  
  versioning:
    enabled: true
    retention-days: 90
  
  graph:
    encryption-on-edges: true
```

### 2. Define Entity with NEW Annotations

```java
@Data
@OneirosEntity("users")
@OneirosTable(isStrict = true, comment = "User accounts")
@OneirosVersioned(maxVersions = 10, historyTable = "user_history")
public class User {
    @OneirosID
    private String id;
    
    @OneirosField(
        type = "string",
        unique = true,
        index = true,
        assertion = "$value.length() > 5"
    )
    private String email;
    
    @OneirosEncrypted  // Auto-encrypted!
    private String password;
    
    @OneirosRelation(target = User.class, type = ONE_TO_MANY)
    private List<String> friends;  // Record links
}
```

### 3. Create Repository

```java
@Repository
public class UserRepository extends SimpleOneirosRepository<User, String> {
    public UserRepository(OneirosClient client, 
                         ObjectMapper mapper,
                         CryptoService crypto) {
        super(client, mapper, crypto);
    }
    
    // Custom queries using Fluent API
    public Flux<User> findActiveAdults() {
        return query()
            .where("age").greaterThanOrEqual(18)
            .and("active").is(true)
            .orderBy("createdAt", "DESC")
            .limit(100)
            .fetch("profile", "settings")  // Eager load relations
            .execute();
    }
}
```

### 4. Use in Controller (All APIs)

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepo;
    private final OneirosGraph graph;
    
    // 1️⃣ Standard Repository Operations
    @PostMapping
    public Mono<User> create(@RequestBody User user) {
        return userRepo.save(user);  // Auto-encrypts, creates history
    }
    
    // 2️⃣ Fluent Query API
    @GetMapping("/adults")
    public Flux<User> getAdults() {
        return userRepo.query()
            .where("age").greaterThanOrEqual(18)
            .omit("password")  // Privacy control
            .execute();
    }
    
    // 3️⃣ Graph & Relate API (NEW)
    @PostMapping("/{userId}/purchase/{productId}")
    public Mono<Void> createPurchase(
        @PathVariable String userId,
        @PathVariable String productId,
        @RequestParam double price
    ) {
        return graph.from("user:" + userId)
            .to("product:" + productId)
            .via("purchased")
            .with("price", price)
            .with("timestamp", Instant.now())
            .execute();
    }
    
    // 4️⃣ Statement API - Complex Transaction
    @PostMapping("/transfer")
    public Mono<Map<String, Object>> transfer(@RequestBody TransferRequest req) {
        return new BeginStatement()
            .add(new LetStatement("$amount", req.getAmount()))
            .add(new UpdateStatement("account:" + req.getFromId())
                .set("balance", "balance - $amount")
                .where("balance >= $amount"))
            .add(new UpdateStatement("account:" + req.getToId())
                .set("balance", "balance + $amount"))
            .add(new IfStatement("account:" + req.getFromId() + ".balance < 0")
                .then(new ThrowStatement("Insufficient funds")))
            .add(new CommitStatement())
            .execute(client);
    }
    
    // 5️⃣ Query Version History (NEW)
    @GetMapping("/{id}/history")
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

## ⚙️ Configuration

### Complete application.yml

```yaml
oneiros:
  # Connection
  url: ws://localhost:8000/rpc
  username: root
  password: root
  namespace: production
  database: main
  
  # Connection Pool
  connection:
    max-connections: 50
    min-idle: 10
    connection-timeout: 30s
    idle-timeout: 10m
  
  # Security
  encryption:
    secret-key: ${ONEIROS_ENCRYPTION_KEY}  # 32-byte base64 key
    algorithm: AES/GCM/NoPadding
  
  # NEW: Auto-Migration
  migration:
    enabled: true
    auto-create-schema: true
    base-package: "com.example.domain"
    dry-run: false
    on-startup: true
  
  # NEW: Versioning
  versioning:
    enabled: true
    retention-days: 90
    store-full-snapshot: true
    track-user: true
  
  # NEW: Graph API
  graph:
    encryption-on-edges: true
    default-timeout: 10s
    validate-record-types: true
```

---

## 🎯 Core APIs

### 1. Fluent Query Builder

The original fluent API for building SELECT queries:

```java
// Simple query
userRepo.query()
    .where("age").greaterThan(18)
    .orderBy("name", "ASC")
    .limit(50)
    .execute();

// Complex query with all clauses
userRepo.query()
    .where("role").in("ADMIN", "MODERATOR")
    .or("specialAccess").is(true)
    .and("active").is(true)
    .omit("password", "secretKey")  // Privacy
    .fetch("profile", "permissions")  // Eager load
    .orderBy("createdAt", "DESC")
    .limit(100)
    .start(20)  // Pagination
    .timeout("5s")
    .parallel()
    .execute();

// Convert to Statement for transactions
SelectStatement<User> stmt = userRepo.query()
    .where("age").greaterThanOrEqual(18)
    .toStatement();
```

### 2. Statement API

Direct SurrealQL statement construction with fluent builders:

```java
// CREATE
new CreateStatement("users")
    .set("name", "Alice")
    .set("email", "alice@example.com")
    .returnAfter()
    .execute(client);

// UPDATE
new UpdateStatement("users")
    .set("verified", true)
    .where("email = 'alice@example.com'")
    .returnDiff()
    .execute(client);

// DELETE
new DeleteStatement("users")
    .where("age < 18")
    .returnBefore()
    .execute(client);

// UPSERT
new UpsertStatement("users")
    .set("name", "Bob")
    .set("email", "bob@example.com")
    .where("email = 'bob@example.com'")
    .execute(client);

// INSERT
new InsertStatement("users")
    .fields("name", "email", "age")
    .values("Charlie", "charlie@example.com", 30)
    .execute(client);

// RELATE
new RelateStatement("person:alice", "knows", "person:bob")
    .set("since", "2020-01-01")
    .set("strength", 0.8)
    .execute(client);
```

### 3. Graph & Relate API (NEW)

Simplified graph relationship management:

```java
// Basic relation
graph.from(user)
    .to(product)
    .via("purchased")
    .with("price", 999.99)
    .with("quantity", 1)
    .execute();

// With entity (auto-encryption)
Purchase purchase = new Purchase();
purchase.setPrice(999.99);
purchase.setPaymentToken("tok_secret");  // @OneirosEncrypted

graph.from("user:alice")
    .to("product:laptop")
    .via("purchased")
    .withEntity(purchase)  // Auto-encrypts sensitive fields
    .returnAfter()
    .execute(Purchase.class);

// Without encryption
graph.from("user:bob")
    .to("product:mouse")
    .via("purchased")
    .withData(Map.of("price", 29.99))
    .withoutEncryption()
    .timeout("5s")
    .execute();

// Query relations
String sql = "SELECT ->purchased->product AS purchases FROM user:alice";
client.query(sql, Map.class);

// Bidirectional
String sql = "SELECT <-purchased<-user AS buyers FROM product:laptop";
client.query(sql, Map.class);

// Generate SQL for debugging
String sql = graph.from("user:alice")
    .to("product:laptop")
    .via("purchased")
    .with("price", 999.99)
    .toSql();
System.out.println(sql);
// Output: RELATE user:alice->purchased->product:laptop CONTENT {"price":999.99}
```

### 4. Auto-Migration Engine (NEW)

Automatic schema generation from annotations:

```java
// Entity with full schema definition
@OneirosTable(
    isStrict = true,
    type = "NORMAL",
    changeFeed = "3d",
    comment = "User accounts"
)
@OneirosVersioned(
    historyTable = "user_history",
    maxVersions = 10,
    retentionDays = 90
)
@OneirosEntity("users")
public class User {
    @OneirosID
    private String id;
    
    @OneirosField(
        type = "string",
        unique = true,
        index = true,
        indexName = "idx_user_email",
        assertion = "$value.length() > 5 AND $value CONTAINS '@'",
        comment = "User email address"
    )
    private String email;
    
    @OneirosField(
        type = "string",
        readonly = false
    )
    private String name;
    
    @OneirosField(
        type = "int",
        defaultValue = "0",
        assertion = "$value >= 0 AND $value <= 150"
    )
    private Integer age;
    
    @OneirosEncrypted
    @OneirosField(type = "string")
    private String password;
    
    @OneirosRelation(
        target = User.class,
        type = RelationType.ONE_TO_MANY
    )
    @OneirosField(type = "array<record<users>>")
    private List<String> friends;
    
    @OneirosField(
        type = "datetime",
        defaultValue = "time::now()",
        readonly = true
    )
    private LocalDateTime createdAt;
}

// Migration happens automatically on startup, or manually:
OneirosMigrationEngine engine = new OneirosMigrationEngine(
    client,
    "com.example.domain",  // Base package to scan
    true,                  // Auto-migrate
    false                  // Not dry-run
);

engine.migrate()
    .doOnSuccess(v -> log.info("Migration completed!"))
    .subscribe();

// Generated SQL:
// DEFINE TABLE users SCHEMAFULL COMMENT 'User accounts';
// DEFINE FIELD email ON users TYPE string 
//   ASSERT $value.length() > 5 AND $value CONTAINS '@'
//   COMMENT 'User email address';
// DEFINE INDEX idx_user_email ON users FIELDS email UNIQUE;
// DEFINE FIELD age ON users TYPE int DEFAULT 0
//   ASSERT $value >= 0 AND $value <= 150;
// DEFINE FIELD createdAt ON users TYPE datetime 
//   DEFAULT time::now() READONLY;
// DEFINE TABLE user_history SCHEMALESS 
//   COMMENT 'Version history for users table';
```

### 5. Automatic Versioning (NEW)

Built-in time-travel and history tracking:

```java
// Enable versioning
@OneirosVersioned(
    historyTable = "user_history",
    maxVersions = 10,
    retentionDays = 90
)
public class User { ... }

// Every update creates history automatically
user.setAge(26);
userRepo.save(user);  // Old version saved to user_history

// Query history
client.query(
    "SELECT * FROM user_history WHERE record_id = $id ORDER BY timestamp DESC",
    Map.of("id", "user:alice"),
    Map.class
);

// Result:
// [
//   {
//     record_id: "user:alice",
//     event_type: "UPDATE",
//     timestamp: "2024-02-05T10:30:00Z",
//     data: {
//       before: { age: 25, name: "Alice" },
//       after: { age: 26, name: "Alice" }
//     }
//   }
// ]

// Time-travel query
client.query(
    "SELECT * FROM user:alice VERSION d'2024-02-05T10:00:00Z'",
    Map.class
);
```

---

## 🔥 Advanced Usage

### Complex Transaction with Mixed APIs

```java
new BeginStatement()
    // 1. Variables
    .add(new LetStatement("$amount", 100))
    .add(new LetStatement("$user_id", "user:alice"))
    
    // 2. Check balance
    .add(new LetStatement("$balance", 
        new SelectStatement("account")
            .where("id = $user_id")
            .field("balance")
    ))
    
    // 3. Conditional logic
    .add(new IfStatement("$balance < $amount")
        .then(new ThrowStatement("Insufficient funds"))
        .elseIf("$amount <= 0")
        .then(new ThrowStatement("Invalid amount"))
        .elseThen(new UpdateStatement("account")
            .set("balance", "balance - $amount")
            .where("id = $user_id")
        )
    )
    
    // 4. Create transaction record using Graph API
    .add(new LetStatement("$tx", 
        graph.from("$user_id")
            .to("account:merchant")
            .via("paid")
            .with("amount", "$amount")
            .with("timestamp", "time::now()")
            .toStatement()
    ))
    
    // 5. Return result
    .add(new ReturnStatement(Map.of(
        "success", true,
        "transaction_id", "$tx.id",
        "amount", "$amount"
    )))
    
    .add(new CommitStatement())
    .execute(client);
```

### FOR Loop with Batch Processing

```java
new ForStatement(
    "$user",
    new SelectStatement("users").where("age < 18")
)
    .add(new UpdateStatement("$user.id")
        .set("minor", true)
        .set("requiresParentalConsent", true)
    )
    .add(new IfStatement("$user.hasParent = false")
        .then(new ThrowStatement("User has no parent guardian"))
    )
    .execute(client);
```

### Conditional Operations

```java
new IfStatement("user.role = 'ADMIN'")
    .then(new UpdateStatement("users")
        .set("permissions", "'full'")
    )
    .elseIf("user.role = 'MODERATOR'")
    .then(new UpdateStatement("users")
        .set("permissions", "'limited'")
    )
    .elseThen(new UpdateStatement("users")
        .set("permissions", "'read-only'")
    )
    .execute(client);
```

---

## 📚 Complete API Reference

### Annotations

#### Core Annotations
- `@OneirosEntity(String tableName)` - Marks a class as a SurrealDB entity
- `@OneirosID` - Marks the ID field
- `@OneirosEncrypted` - Automatic field encryption

#### NEW: Schema Annotations
- `@OneirosTable` - Table-level configuration
  - `isStrict` - SCHEMAFULL vs SCHEMALESS
  - `type` - NORMAL or RELATION
  - `changeFeed` - Enable change feed
  - `comment` - Table description

- `@OneirosField` - Field-level configuration
  - `type` - SurrealDB type (string, int, datetime, etc.)
  - `unique` - Unique constraint
  - `index` - Create index
  - `indexName` - Custom index name
  - `assertion` - Validation rule
  - `defaultValue` - Default value
  - `readonly` - Read-only field
  - `comment` - Field description

- `@OneirosRelation` - Relationship definition
  - `target` - Target entity class
  - `type` - ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY

- `@OneirosVersioned` - Enable versioning
  - `historyTable` - History table name
  - `maxVersions` - Max versions to keep
  - `retentionDays` - Retention period

### Statement Classes

All statements follow fluent builder pattern:

**Data Operations:**
- `CreateStatement` - CREATE records
- `UpdateStatement` - UPDATE records
- `DeleteStatement` - DELETE records
- `UpsertStatement` - UPSERT (insert or update)
- `InsertStatement` - INSERT with VALUES
- `SelectStatement` - SELECT queries
- `RelateStatement` - RELATE for graph edges

**Control Flow:**
- `IfStatement` - IF/ELSE IF/ELSE conditionals
- `ForStatement` - FOR loops
- `BreakStatement` - Break loop
- `ContinueStatement` - Continue loop

**Transactions:**
- `BeginStatement` - BEGIN TRANSACTION
- `CommitStatement` - COMMIT
- `CancelStatement` - CANCEL/ROLLBACK

**Variables:**
- `LetStatement` - LET variable assignment
- `ReturnStatement` - RETURN value
- `ThrowStatement` - THROW error

### Graph API

**OneirosGraph Methods:**
```java
graph.from(Object source)           // Start relation
    .to(Object target)              // End relation
    .via(String edgeTable)          // Edge table name
    .with(String key, Object value) // Add field
    .withData(Map<String, Object>)  // Add multiple fields
    .withEntity(Object entity)      // Add entity (with encryption)
    .withoutEncryption()            // Disable encryption
    .returnBefore()                 // RETURN BEFORE
    .returnAfter()                  // RETURN AFTER (default)
    .returnDiff()                   // RETURN DIFF
    .timeout(String duration)       // TIMEOUT clause
    .toSql()                        // Generate SQL
    .toStatement()                  // Convert to Statement
    .execute()                      // Execute
    .execute(Class<T>)              // Execute with type
```

### Migration Engine

**OneirosMigrationEngine:**
```java
engine.migrate()                    // Run migration
engine.generateMigrationSQL()       // Preview SQL
engine.validateSchema()             // Validate only
engine.rollback()                   // Rollback changes
```

---

## 💡 Best Practices

### 1. Security
- ✅ Always use `@OneirosEncrypted` for sensitive data
- ✅ Store encryption keys in environment variables
- ✅ Use `.omit()` to exclude sensitive fields from responses
- ✅ Enable `graph.encryption-on-edges` for sensitive relationships

### 2. Performance
- ✅ Use `.fetch()` for eager loading instead of N+1 queries
- ✅ Add indexes on frequently queried fields with `@OneirosField(index = true)`
- ✅ Use `.parallel()` for independent operations
- ✅ Set appropriate `.timeout()` for long-running queries

### 3. Schema Design
- ✅ Use `@OneirosTable(isStrict = true)` for production tables
- ✅ Add meaningful `assertion` rules for data validation
- ✅ Use `@OneirosVersioned` for audit-critical entities
- ✅ Define proper `@OneirosRelation` types for referential integrity

### 4. Transactions
- ✅ Always use BEGIN/COMMIT for multi-step operations
- ✅ Add error handling with IF/THROW
- ✅ Use LET variables for reusable values
- ✅ RETURN meaningful results

---

## 🎓 Comprehensive Examples

See the following files for complete examples:
- `AutoConversionExample.java` - Basic CRUD operations
- `AdvancedFeaturesDemo.java` - All advanced features
- `IntegratedQueryDemoRunner.java` - Complete API integration
- `ADVANCED_FEATURES.md` - Detailed feature documentation

---

## 🐛 Troubleshooting

### Common Issues

**1. "No beans of 'OneirosProperties' type found"**
```java
// Solution: Add @EnableOneiros to your @SpringBootApplication
@SpringBootApplication
@EnableOneiros
public class Application { }
```

**2. Migration not running**
```yaml
# Solution: Enable in application.yml
oneiros:
  migration:
    enabled: true
    on-startup: true
```

**3. Encryption errors**
```yaml
# Solution: Set valid 32-byte base64 key
oneiros:
  encryption:
    secret-key: ${ONEIROS_ENCRYPTION_KEY}
```

**4. Connection timeout**
```yaml
# Solution: Increase timeout
oneiros:
  connection:
    connection-timeout: 60s
```

---

## 📖 Documentation

- [ADVANCED_FEATURES.md](ADVANCED_FEATURES.md) - Detailed feature documentation
- [STATEMENT_API_COMPLETE.md](STATEMENT_API_COMPLETE.md) - Complete statement API reference
- [Examples Directory](src/main/java/io/oneiros/test/) - Working code examples

---

## 🤝 Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Add tests for new features
4. Submit a pull request

---

## 📄 License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgments

- Built on [SurrealDB](https://surrealdb.com/) - The ultimate multi-model database
- Powered by [Project Reactor](https://projectreactor.io/) - Reactive programming
- Integrated with [Spring Boot](https://spring.io/projects/spring-boot)

---

<div align="center">

**Made with ❤️ by the Oneiros Team**

[GitHub](https://github.com/mzxidev/oneiros-core) • [Issues](https://github.com/mzxidev/oneiros-core/issues) • [Discussions](https://github.com/mzxidev/oneiros-core/discussions)

</div>
