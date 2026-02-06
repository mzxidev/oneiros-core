# 🌙 Oneiros - Reactive SurrealDB Client for Java

<div align="center">

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![SurrealDB](https://img.shields.io/badge/SurrealDB-2.0+-purple.svg)](https://surrealdb.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**A modern, type-safe, reactive client library for SurrealDB with intuitive fluent APIs and comprehensive SurrealQL support.**

[Features](#-features) • [Quick Start](#-quick-start) • [Documentation](#-core-concepts) • [Examples](#-examples) • [API Reference](#-complete-api-reference)

</div>

---

## 📋 Table of Contents

- [Features](#-features)
- [Installation](#-installation)
- [Quick Start](#-quick-start)
- [Configuration](#-configuration)
- [Core Concepts](#-core-concepts)
  - [Entities & Annotations](#entities--annotations)
  - [Repository Pattern](#repository-pattern)
  - [Fluent Query API](#fluent-query-api)
  - [Statement API](#statement-api)
- [Advanced Features](#-advanced-features)
  - [Transactions](#transactions)
  - [Graph Queries](#graph-queries)
  - [Security & Encryption](#security--encryption)
- [Complete API Reference](#-complete-api-reference)
- [Examples](#-examples)
- [Best Practices](#-best-practices)
- [Troubleshooting](#-troubleshooting)

---

## ✨ Features

### 🚀 **Core Capabilities**
- ✅ **Reactive & Non-Blocking** - Built on Project Reactor for high-performance async operations
- ✅ **Type-Safe** - Full compile-time type checking with Java generics
- ✅ **Fluent API** - Intuitive, chainable query builder
- ✅ **Statement API** - Direct SurrealQL statement construction
- ✅ **Spring Boot Integration** - Auto-configuration and dependency injection
- ✅ **Annotation-Based** - Simple entity mapping with `@OneirosEntity`, `@OneirosID`, `@OneirosEncrypted`

### 🔥 **Advanced Features**
- 🔐 **Built-in Encryption** - Automatic field-level AES encryption
- 📊 **Graph Queries** - Native support for SurrealDB's graph relations (`->knows->`, `<-follows<-`)
- 🔄 **Transactions** - ACID transactions with BEGIN/COMMIT/ROLLBACK
- 🎯 **Control Flow** - IF/ELSE, FOR loops, LET variables, THROW/RETURN statements
- ⚡ **Query Optimization** - TIMEOUT, PARALLEL execution, START/LIMIT pagination
- 🎨 **Privacy Control** - OMIT sensitive fields, FETCH related records

### 🚀 **Real-time & Performance Features**
- 🔴 **Live Queries** - Real-time data streams with LIVE SELECT
- 🏊 **Connection Pooling** - Load balancing with multiple WebSocket connections
- 🔍 **Full-Text Search** - BM25 ranking, highlights, custom analyzers
- 🛡️ **Circuit Breaker** - Automatic failure detection and recovery
- 📈 **Health Monitoring** - Spring Boot Actuator integration

### 📈 **SurrealQL Support**
- ✅ **Data Operations**: SELECT, CREATE, UPDATE, DELETE, UPSERT, INSERT
- ✅ **Graph Operations**: RELATE for bidirectional relationships
- ✅ **Control Flow**: IF/ELSE, FOR, BREAK, CONTINUE
- ✅ **Transactions**: BEGIN, COMMIT, CANCEL
- ✅ **Variables**: LET, RETURN, THROW
- ✅ **Clauses**: WHERE, ORDER BY, LIMIT, START, FETCH, OMIT, TIMEOUT, PARALLEL

---

## 📦 Installation

### Gradle (build.gradle)

**Important:** Add the JitPack repository to your build file:

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }  // Required for Oneiros
}

dependencies {
    implementation 'com.github.mzxidev:oneiros-core:v0.2.0'
    
    // Required dependencies (if not already present)
    implementation 'org.springframework.boot:spring-boot-starter-webflux:3.2.0'
    implementation 'io.projectreactor:reactor-core:3.6.0'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.15.2'
}
```

### Maven (pom.xml)

**Important:** Add the JitPack repository:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.mzxidev</groupId>
        <artifactId>oneiros-core</artifactId>
        <version>v0.2.0</version>
    </dependency>
</dependencies>
```

---

## 🚀 Quick Start

### 1️⃣ Configuration (application.yml)

```yaml
spring:
  main:
    web-application-type: reactive

oneiros:
  url: "ws://127.0.0.1:8000/rpc"
  username: "root"
  password: "root"
  namespace: "my_namespace"
  database: "my_database"
  security:
    enabled: true
    key: "SuperSecretKey123456789" # Must be > 16 characters
  cache:
    enabled: true
    ttl-seconds: 60
```

### 2️⃣ Define Your Entity

```java
import io.oneiros.annotation.*;

@OneirosEntity("users")
public class User {
    
    @OneirosID
    private String id;
    
    private String name;
    private String email;
    
    @OneirosEncrypted
    private String password;
    
    private Integer age;
    private Boolean verified;
    
    // Getters and Setters...
}
```

### 3️⃣ Create a Repository

```java
import io.oneiros.core.SimpleOneirosRepository;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository extends SimpleOneirosRepository<User> {
    
    public UserRepository(OneirosClient client) {
        super(client, User.class);
    }
    
    // Custom query methods
    public Mono<User> findByEmail(String email) {
        return OneirosQuery.select(User.class)
            .where("email").is(email)
            .executeOne(client);
    }
}
```

### 4️⃣ Use in Your Service

```java
@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final OneirosClient client;
    
    // Simple CRUD
    public Mono<User> createUser(User user) {
        return userRepository.save(user);
    }
    
    // Fluent Query API
    public Flux<User> findAdultUsers() {
        return OneirosQuery.select(User.class)
            .where("age").greaterThanOrEqual(18)
            .where("verified").is(true)
            .orderBy("name", "ASC")
            .limit(50)
            .execute(client);
    }
    
    // Statement API
    public Mono<User> updateUserEmail(String userId, String newEmail) {
        return UpdateStatement.table(User.class)
            .set("email", newEmail)
            .where("id = user:" + userId)
            .executeOne(client);
    }
}
```

---

## ⚙️ Configuration

### Complete Configuration Reference

```yaml
oneiros:
  # Database Connection
  url: "ws://127.0.0.1:8000/rpc"        # WebSocket URL to SurrealDB
  username: "root"                       # Database username
  password: "root"                       # Database password
  namespace: "my_namespace"              # SurrealDB namespace
  database: "my_database"                # SurrealDB database
  auto-connect: true                     # Connect on startup (default: true)
  
  # Security Settings
  security:
    enabled: true                        # Enable field-level encryption
    key: "YourSecretKey123456789"       # AES encryption key (min 16 chars)
  
  # Cache Settings
  cache:
    enabled: true                        # Enable query result caching
    ttl-seconds: 60                     # Cache time-to-live in seconds
  
  # Connection Pool (optional)
  connection-pool:
    enabled: false                       # Enable connection pooling
    size: 5                              # Number of connections in pool
    health-check-interval-seconds: 30   # Health check interval
    reconnect-delay-seconds: 5          # Reconnection delay
```

### Connection Modes

Oneiros supports two connection modes:

#### **Auto-Connect (Recommended)**
```yaml
oneiros:
  auto-connect: true  # Connect immediately on startup
```
✅ **Benefits:**
- Fails fast on startup if configuration is wrong
- Connection ready immediately for first request
- Shows clear connection status in startup logs

#### **Lazy Connect**
```yaml
oneiros:
  auto-connect: false  # Connect on first request
```
⚠️ **Use Cases:**
- When SurrealDB might not be available on startup
- For applications with optional database features

### Health Monitoring

Once configured, you can check connection status via:

**Spring Boot Actuator:**
```bash
curl http://localhost:8080/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "oneirosHealthIndicator": {
      "status": "UP",
      "details": {
        "status": "Connected",
        "type": "OneirosWebsocketClient"
      }
    }
  }
}
```

### Environment Variables

```bash
export ONEIROS_URL=ws://127.0.0.1:8000/rpc
export ONEIROS_USERNAME=root
export ONEIROS_PASSWORD=root
export ONEIROS_NAMESPACE=my_namespace
export ONEIROS_DATABASE=my_database
export ONEIROS_SECURITY_KEY=MySecretKey123
```

---

## 🎯 Core Concepts

### Entities & Annotations

#### `@OneirosEntity`
Marks a class as a SurrealDB entity and specifies the table name.

```java
@OneirosEntity("users")  // Table name: "users"
public class User { }

@OneirosEntity           // Table name: auto-derived as "product"
public class Product { }
```

#### `@OneirosID`
Marks a field as the record ID.

```java
public class User {
    @OneirosID
    private String id;  // Will be "user:abc123" in SurrealDB
}
```

#### `@OneirosEncrypted`
Marks a field for automatic encryption/decryption.

```java
public class User {
    @OneirosEncrypted
    private String password;  // Automatically encrypted before saving
}
```

---

### Repository Pattern

#### SimpleOneirosRepository

```java
@Repository
public class UserRepository extends SimpleOneirosRepository<User> {
    
    public UserRepository(OneirosClient client) {
        super(client, User.class);
    }
    
    // Inherited methods:
    // - Mono<User> save(User entity)
    // - Flux<User> saveAll(List<User> entities)
    // - Mono<User> findById(String id)
    // - Flux<User> findAll()
    // - Mono<Void> deleteById(String id)
}
```

#### Custom Query Methods

```java
@Repository
public class UserRepository extends SimpleOneirosRepository<User> {
    
    // Custom query using Fluent API
    public Flux<User> findByRole(String role) {
        return OneirosQuery.select(User.class)
            .where("role").is(role)
            .execute(client);
    }
    
    // Complex query with multiple conditions
    public Flux<User> findActiveAdmins() {
        return OneirosQuery.select(User.class)
            .where("role").is("ADMIN")
            .where("active").is(true)
            .where("last_login").greaterThan("2024-01-01")
            .orderBy("name", "ASC")
            .limit(100)
            .execute(client);
    }
}
```

---

### Fluent Query API

The **Fluent Query API** provides an intuitive, chainable interface for building queries.

#### Basic SELECT Queries

```java
// Select all users
OneirosQuery.select(User.class)
    .execute(client);

// Select with WHERE condition
OneirosQuery.select(User.class)
    .where("age").greaterThanOrEqual(18)
    .execute(client);

// Multiple conditions (AND)
OneirosQuery.select(User.class)
    .where("age").greaterThanOrEqual(18)
    .where("verified").is(true)
    .where("role").is("ADMIN")
    .execute(client);

// OR conditions
OneirosQuery.select(User.class)
    .where("role").is("ADMIN")
    .or("role").is("MODERATOR")
    .execute(client);
```

#### WHERE Clause Operators

```java
// Comparison operators
.where("age").is(25)                    // age = 25
.where("age").notEquals(25)             // age != 25
.where("age").greaterThan(18)           // age > 18
.where("age").greaterThanOrEqual(18)    // age >= 18
.where("age").lessThan(65)              // age < 65
.where("age").lessThanOrEqual(65)       // age <= 65

// IN operator
.where("role").in("ADMIN", "MODERATOR") // role IN ['ADMIN', 'MODERATOR']

// LIKE operator (pattern matching)
.where("email").like("%@gmail.com")     // email ~ '%@gmail.com'

// BETWEEN operator
.where("age").between(18, 65)           // age >= 18 AND age <= 65

// NULL checks
.where("deleted_at").isNull()           // deleted_at IS NULL
.where("deleted_at").isNotNull()        // deleted_at IS NOT NULL
```

#### Sorting & Pagination

```java
// ORDER BY
OneirosQuery.select(User.class)
    .orderBy("name", "ASC")
    .execute(client);

// LIMIT
OneirosQuery.select(User.class)
    .limit(50)
    .execute(client);

// START + LIMIT (pagination with offset)
OneirosQuery.select(User.class)
    .limit(50)
    .start(100)  // START 100 LIMIT 50
    .execute(client);
```

#### Privacy & Performance

```java
// OMIT - Exclude sensitive fields
OneirosQuery.select(User.class)
    .omit("password", "ssn", "credit_card")
    .execute(client);

// FETCH - Load related records (graph traversal)
OneirosQuery.select(User.class)
    .fetch("profile", "permissions", "posts")
    .execute(client);

// TIMEOUT - Set query timeout
OneirosQuery.select(User.class)
    .timeout(Duration.ofSeconds(5))
    .execute(client);

// PARALLEL - Enable parallel execution
OneirosQuery.select(User.class)
    .parallel()
    .execute(client);
```

#### Complete Example

```java
// Find verified adult users, exclude sensitive data, sort, and paginate
Flux<User> users = OneirosQuery.select(User.class)
    .where("age").greaterThanOrEqual(18)
    .where("verified").is(true)
    .where("role").in("USER", "PREMIUM")
    .omit("password", "ssn")
    .fetch("profile", "preferences")
    .orderBy("created_at", "DESC")
    .limit(50)
    .start(0)
    .timeout(Duration.ofSeconds(3))
    .execute(client);
```

---

### Statement API

The **Statement API** provides direct control over SurrealQL statements.

#### CREATE Statement

```java
// Create with random ID
CreateStatement.table(User.class)
    .set("name", "Alice")
    .set("email", "alice@example.com")
    .set("age", 25)
    .executeOne(client);

// Create with specific ID
CreateStatement.record(User.class, "alice")
    .set("name", "Alice")
    .set("email", "alice@example.com")
    .executeOne(client);

// Create with CONTENT (object)
User user = new User();
user.setName("Alice");
user.setEmail("alice@example.com");

CreateStatement.table(User.class)
    .content(user)
    .executeOne(client);
```

#### UPDATE Statement

```java
// Update specific record
UpdateStatement.record(User.class, "alice")
    .set("verified", true)
    .set("verified_at", "2024-01-15")
    .executeOne(client);

// Update with WHERE condition
UpdateStatement.table(User.class)
    .set("verified", true)
    .where("email = 'alice@example.com'")
    .execute(client);

// Update with RETURN clause
UpdateStatement.table(User.class)
    .set("verified", true)
    .where("email = 'alice@example.com'")
    .returnBefore()  // RETURN BEFORE
    .executeOne(client);
```

#### DELETE Statement

```java
// Delete specific record
DeleteStatement.record(User.class, "alice")
    .executeOne(client);

// Delete with WHERE condition
DeleteStatement.from(User.class)
    .where("age < 18")
    .execute(client);

// Delete with RETURN clause
DeleteStatement.from(User.class)
    .where("verified = false")
    .returnBefore()
    .execute(client);
```

#### UPSERT Statement

```java
// Upsert (insert or update)
UpsertStatement.table(User.class)
    .set("name", "Bob")
    .set("email", "bob@example.com")
    .where("email = 'bob@example.com'")
    .executeOne(client);

// Upsert specific record
UpsertStatement.record(User.class, "bob")
    .set("name", "Bob")
    .set("email", "bob@example.com")
    .executeOne(client);
```

#### INSERT Statement

```java
// Insert with VALUES
InsertStatement.into(User.class)
    .fields("name", "email", "age")
    .values("Charlie", "charlie@example.com", 30)
    .executeOne(client);

// Insert multiple records
InsertStatement.into(User.class)
    .fields("name", "email", "age")
    .values("Alice", "alice@example.com", 25)
    .values("Bob", "bob@example.com", 30)
    .execute(client);
```

#### SELECT Statement

```java
// Basic SELECT
SelectStatement.from(User.class)
    .execute(client);

// SELECT with WHERE
SelectStatement.from(User.class)
    .where("age >= 18")
    .execute(client);

// SELECT with ORDER BY
SelectStatement.from(User.class)
    .where("verified = true")
    .orderBy("name")
    .execute(client);

// SELECT with LIMIT
SelectStatement.from(User.class)
    .limit(50)
    .execute(client);
```

---

## 🔴 Real-time & Performance Features

### Live Queries

Subscribe to real-time updates from SurrealDB using LIVE SELECT.

#### Basic Live Query

```java
@Service
public class ProductService {
    
    private final OneirosLiveManager liveManager;
    
    // Subscribe to product price changes
    public Flux<OneirosEvent<Product>> watchPriceChanges() {
        return liveManager.live(Product.class)
            .where("price < 100")
            .subscribe();
    }
    
    // Handle events
    public void startWatching() {
        watchPriceChanges()
            .subscribe(event -> {
                switch (event.getAction()) {
                    case CREATE -> log.info("New product: {}", event.getData());
                    case UPDATE -> log.info("Updated: {}", event.getData());
                    case DELETE -> log.info("Deleted: {}", event.getId());
                }
            });
    }
}
```

#### Live Query with Encryption

Encrypted fields are automatically decrypted in live events:

```java
@Service
public class UserMonitoringService {
    
    private final OneirosLiveManager liveManager;
    
    public Flux<OneirosEvent<User>> watchAdminActions() {
        return liveManager.live(User.class)
            .where("role = 'ADMIN'")
            .withDecryption(true)  // Auto-decrypt @OneirosEncrypted fields
            .subscribe();
    }
}
```

#### Live Query Event Structure

```java
public class OneirosEvent<T> {
    private LiveAction action;  // CREATE, UPDATE, DELETE
    private String id;          // Record ID
    private T data;             // Full record (for CREATE/UPDATE)
    private T before;           // Previous state (for UPDATE)
    private Instant timestamp;  // Event timestamp
}
```

### Connection Pooling

Enable connection pooling for high-traffic applications.

#### Configuration

```yaml
oneiros:
  connection-pool:
    enabled: true                          # Enable connection pool
    size: 5                                # Number of WebSocket connections
    health-check-interval-seconds: 30      # Health check interval
    reconnect-delay-seconds: 5             # Reconnect delay on failure
```

#### Features

- ✅ **Round-robin load balancing** - Distributes queries across connections
- ✅ **Automatic health checks** - Detects and removes dead connections
- ✅ **Auto-reconnection** - Rebuilds failed connections automatically
- ✅ **Transparent** - No code changes needed

#### Usage

```java
@Service
public class HighTrafficService {
    
    @Autowired
    private OneirosClient client;  // Auto-wired as pool if enabled
    
    // All queries automatically use the connection pool
    public Flux<Product> getProducts() {
        return OneirosQuery.select(Product.class)
            .execute(client);  // Uses next available connection
    }
}
```

### Full-Text Search

Powerful full-text search with BM25 ranking and custom analyzers.

#### Mark Fields for Search

```java
@OneirosEntity("products")
public class Product {
    
    @OneirosFullText(analyzer = "ascii", bm25 = true)
    private String description;
    
    @OneirosFullText(analyzer = "unicode")
    private String title;
}
```

#### Search API

```java
@Service
public class SearchService {
    
    private final OneirosClient client;
    
    // Basic search
    public Flux<Product> searchProducts(String query) {
        return OneirosSearch.table(Product.class)
            .content("description")
            .matching(query)
            .execute(client);
    }
    
    // Advanced search with scoring
    public Flux<Product> advancedSearch(String query) {
        return OneirosSearch.table(Product.class)
            .content("description", "title")
            .matching(query)
            .withScoring()
            .minScore(0.7)
            .withHighlights()
            .limit(20)
            .execute(client);
    }
}
```

#### Generated SurrealQL

```sql
-- Basic search
SELECT * FROM products 
WHERE description @@ 'wireless headphones';

-- Advanced search with scoring
SELECT *, search::score(1) AS relevance
FROM products
WHERE description @@ 'wireless headphones'
   OR title @@ 'wireless headphones'
ORDER BY relevance DESC
LIMIT 20;
```

#### Search Results with Highlights

```json
{
  "id": "product:123",
  "name": "Premium Headphones",
  "description": "<mark>Wireless</mark> Bluetooth 5.0 <mark>headphones</mark>",
  "relevance": 0.92
}
```

### Health Monitoring

Spring Boot Actuator integration for monitoring.

#### Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  endpoint:
    health:
      show-details: always
```

#### Health Endpoint

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
        "deadConnections": 0,
        "totalQueries": 1523,
        "failedQueries": 3
      }
    }
  }
}
```

---

## 🔥 Advanced Features

### Transactions

Execute multiple statements atomically using transactions.

#### Basic Transaction

```java
TransactionStatement transaction = TransactionStatement.begin();

transaction
    .add(CreateStatement.table(User.class)
        .set("name", "Test User")
        .set("balance", 1000))
    
    .add(UpdateStatement.table(Account.class)
        .set("balance -= 100")
        .where("id = account:sender"))
    
    .add(UpdateStatement.table(Account.class)
        .set("balance += 100")
        .where("id = account:receiver"));

transaction.commit().execute(client).subscribe();
```

#### Transaction with IF/ELSE Logic

```java
TransactionStatement transaction = TransactionStatement.begin();

transaction
    .add(LetStatement.variable("amount", "100"))
    
    .add(IfStatement.condition("account:sender.balance >= $amount")
        .then(builder -> builder
            .update(Account.class)
            .set("balance -= $amount")
            .where("id = account:sender"))
        .elseBlock(builder -> builder
            .throwError("Insufficient funds")))
    
    .add(UpdateStatement.table(Account.class)
        .set("balance += $amount")
        .where("id = account:receiver"));

transaction.commit().execute(client).subscribe();
```

#### Transaction with FOR Loop

```java
TransactionStatement transaction = TransactionStatement.begin();

transaction
    .add(ForStatement.variable("$user")
        .in("(SELECT * FROM users WHERE role = 'PREMIUM')")
        .do(builder -> builder
            .update(User.class)
            .set("discount = 0.2")
            .where("id = $user.id")));

transaction.commit().execute(client).subscribe();
```

#### Convert Fluent Query to Transaction

```java
// Start with a fluent query
OneirosQuery<User> query = OneirosQuery.select(User.class)
    .where("age").greaterThanOrEqual(18)
    .where("verified").is(true)
    .limit(100);

// Convert to SelectStatement
SelectStatement<User> selectStmt = query.toSelectStatement();

// Use in transaction
TransactionStatement transaction = TransactionStatement.begin();
transaction.add(selectStmt);
transaction.add(UpdateStatement.table(User.class)
    .set("notified", true)
    .where("verified = true"));

transaction.commit().execute(client).subscribe();
```

---

### Graph Queries

SurrealDB's powerful graph capabilities for modeling relationships.

#### Creating Graph Relations

```java
// RELATE statement - create bidirectional relationship
RelateStatement.from("person:alice")
    .via("knows")
    .to("person:bob")
    .set("since", "2020-01-01")
    .set("strength", 0.8)
    .execute(client);

// Multiple relations
RelateStatement.from("person:alice")
    .via("knows")
    .to("person:bob")
    .execute(client);

RelateStatement.from("person:alice")
    .via("follows")
    .to("person:charlie")
    .execute(client);
```

#### Querying Graph Relations

```java
// Find all people that Alice knows
String query = "SELECT * FROM person:alice->knows->person";
client.query(query, Person.class);

// Find friends of friends
String query = "SELECT * FROM person:alice->knows->person->knows->person";
client.query(query, Person.class);

// Bidirectional queries
String query = "SELECT * FROM person:alice<->knows<->person";
client.query(query, Person.class);

// Filter graph traversal
String query = "SELECT * FROM person:alice->knows[WHERE strength > 0.5]->person";
client.query(query, Person.class);
```

#### Graph Entities

```java
@OneirosEntity("person")
public class Person {
    @OneirosID
    private String id;
    private String name;
}

@OneirosEntity("knows")
public class Knows {
    @OneirosID
    private String id;
    
    private String in;   // Source person
    private String out;  // Target person
    private String since;
    private Double strength;
}

// Query with FETCH to load related records
OneirosQuery.select(Person.class)
    .fetch("->knows->person")
    .where("id = person:alice")
    .execute(client);
```

---

### Security & Encryption

#### Field-Level Encryption

Oneiros provides automatic field-level encryption using AES.

##### 1. Enable Encryption in Configuration

```yaml
oneiros:
  security:
    enabled: true
    key: "MyVerySecretKey12345678"  # Min 16 characters
```

##### 2. Mark Fields for Encryption

```java
@OneirosEntity("users")
public class User {
    @OneirosID
    private String id;
    
    private String name;
    
    @OneirosEncrypted
    private String password;
    
    @OneirosEncrypted
    private String ssn;
    
    @OneirosEncrypted
    private String creditCard;
}
```

##### 3. Automatic Encryption/Decryption

```java
User user = new User();
user.setName("Alice");
user.setPassword("MySecretPassword");  // Plain text

// Save - password is automatically encrypted before sending to DB
userRepository.save(user).subscribe();

// Retrieve - password is automatically decrypted when reading from DB
userRepository.findById("alice")
    .subscribe(retrievedUser -> {
        String password = retrievedUser.getPassword();  // Plain text
    });
```

---

## 📚 Complete API Reference

### OneirosQuery (Fluent API)

```java
// SELECT Methods
static <T> OneirosQuery<T> select(Class<T> type)
OneirosQuery<T> where(String field)
OneirosQuery<T> and(String field)
OneirosQuery<T> or(String field)
OneirosQuery<T> orderBy(String field, String direction)
OneirosQuery<T> limit(int limit)
OneirosQuery<T> start(int start)
OneirosQuery<T> omit(String... fields)
OneirosQuery<T> fetch(String... fields)
OneirosQuery<T> timeout(Duration duration)
OneirosQuery<T> parallel()
String toSql()
SelectStatement<T> toSelectStatement()
Flux<T> execute(OneirosClient client)
Mono<T> executeOne(OneirosClient client)
```

### WHERE Clause Builders

```java
WhereClauseBuilder is(Object value)
WhereClauseBuilder notEquals(Object value)
WhereClauseBuilder greaterThan(Object value)
WhereClauseBuilder greaterThanOrEqual(Object value)
WhereClauseBuilder lessThan(Object value)
WhereClauseBuilder lessThanOrEqual(Object value)
WhereClauseBuilder in(Object... values)
WhereClauseBuilder like(String pattern)
WhereClauseBuilder between(Object start, Object end)
WhereClauseBuilder isNull()
WhereClauseBuilder isNotNull()
```

### Statement API Entrypoints

```java
// Via OneirosQuery
static <T> CreateStatement<T> create(Class<T> type)
static <T> UpdateStatement<T> update(Class<T> type)
static <T> DeleteStatement<T> delete(Class<T> type)
static <T> UpsertStatement<T> upsert(Class<T> type)
static <T> InsertStatement<T> insert(Class<T> type)
static <T> SelectStatement<T> select(Class<T> type)

// Direct Statement Classes
CreateStatement.table(Class<T> type)
CreateStatement.record(Class<T> type, String id)
UpdateStatement.table(Class<T> type)
UpdateStatement.record(Class<T> type, String id)
DeleteStatement.from(Class<T> type)
DeleteStatement.record(Class<T> type, String id)
UpsertStatement.table(Class<T> type)
UpsertStatement.record(Class<T> type, String id)
InsertStatement.into(Class<T> type)
SelectStatement.from(Class<T> type)
RelateStatement.from(String fromRecord)
TransactionStatement.begin()
```

### Control Flow Statements

```java
// IF/ELSE
IfStatement.condition(String condition)
    .then(Consumer<StatementBuilder> thenBlock)
    .elseIf(String condition)
    .elseBlock(Consumer<StatementBuilder> elseBlock)

// FOR Loop
ForStatement.variable(String variable)
    .in(String iterable)
    .do(Consumer<StatementBuilder> doBlock)

// Variables
LetStatement.variable(String name, String value)

// Return & Error Handling
ReturnStatement.value(String expression)
ThrowStatement.error(String message)
BreakStatement.create()
ContinueStatement.create()
SleepStatement.duration(String duration)
```

---

## 💡 Examples

### Example 1: User Registration System

```java
@Service
public class UserRegistrationService {
    
    private final UserRepository userRepository;
    
    public Mono<User> registerUser(String name, String email, String password) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(password);  // Will be encrypted automatically
        user.setVerified(false);
        
        return userRepository.save(user);
    }
    
    public Mono<User> verifyUser(String userId) {
        return UpdateStatement.record(User.class, userId)
            .set("verified", true)
            .set("verified_at", LocalDateTime.now().toString())
            .executeOne(client);
    }
    
    public Mono<User> login(String email, String password) {
        return OneirosQuery.select(User.class)
            .where("email").is(email)
            .where("password").is(password)
            .where("verified").is(true)
            .executeOne(client);
    }
}
```

### Example 2: E-Commerce Order System

```java
@Service
public class OrderService {
    
    private final OneirosClient client;
    
    public Mono<Order> createOrder(String userId, List<OrderItem> items) {
        TransactionStatement transaction = TransactionStatement.begin();
        
        double total = items.stream()
            .mapToDouble(item -> item.getPrice() * item.getQuantity())
            .sum();
        
        // Create order
        transaction.add(CreateStatement.table(Order.class)
            .set("user_id", userId)
            .set("items", items)
            .set("total", total)
            .set("status", "PENDING"));
        
        // Update inventory
        for (OrderItem item : items) {
            transaction.add(UpdateStatement.table(Product.class)
                .set("stock -= " + item.getQuantity())
                .where("id = product:" + item.getProductId()));
        }
        
        // Check balance
        transaction.add(IfStatement
            .condition("user:" + userId + ".balance < " + total)
            .then(builder -> builder.throwError("Insufficient balance")));
        
        // Deduct balance
        transaction.add(UpdateStatement.record(User.class, userId)
            .set("balance -= " + total));
        
        return transaction.commit()
            .execute(client)
            .next()
            .cast(Order.class);
    }
}
```

### Example 3: Social Network

```java
@Service
public class SocialNetworkService {
    
    private final OneirosClient client;
    
    // Create friendship
    public Mono<Void> followUser(String followerId, String followeeId) {
        return RelateStatement
            .from("person:" + followerId)
            .via("follows")
            .to("person:" + followeeId)
            .set("since", LocalDateTime.now().toString())
            .execute(client)
            .then();
    }
    
    // Get followers
    public Flux<Person> getFollowers(String userId) {
        String query = "SELECT * FROM person:" + userId + "<-follows<-person";
        return client.query(query, Person.class);
    }
    
    // Get following
    public Flux<Person> getFollowing(String userId) {
        String query = "SELECT * FROM person:" + userId + "->follows->person";
        return client.query(query, Person.class);
    }
    
    // Get friend suggestions (friends of friends)
    public Flux<Person> getFriendSuggestions(String userId) {
        String query = """
            SELECT * FROM person:%s->follows->person->follows->person
            WHERE id != person:%s
            AND id NOT IN (SELECT VALUE id FROM person:%s->follows->person)
            LIMIT 10
            """.formatted(userId, userId, userId);
        
        return client.query(query, Person.class);
    }
}
```

---

## 🎯 Best Practices

### 1. Use Repository Pattern

✅ **Good** - Centralized data access logic
```java
@Repository
public class UserRepository extends SimpleOneirosRepository<User> {
    
    public Flux<User> findByRole(String role) {
        return OneirosQuery.select(User.class)
            .where("role").is(role)
            .execute(client);
    }
}
```

### 2. Handle Errors Gracefully

✅ **Good** - Proper error handling
```java
userRepository.findById(userId)
    .switchIfEmpty(Mono.error(new UserNotFoundException(userId)))
    .doOnError(error -> log.error("Error fetching user", error))
    .onErrorResume(error -> Mono.just(new User()));
```

### 3. Use Transactions for Multi-Step Operations

✅ **Good** - Atomic transaction
```java
TransactionStatement transaction = TransactionStatement.begin();
transaction.add(UpdateStatement.table(Account.class)
    .set("balance -= 100")
    .where("id = account:sender"));
transaction.add(UpdateStatement.table(Account.class)
    .set("balance += 100")
    .where("id = account:receiver"));
transaction.commit().execute(client).subscribe();
```

### 4. Leverage Field-Level Encryption

✅ **Good** - Encrypt sensitive data
```java
@OneirosEntity("users")
public class User {
    @OneirosEncrypted
    private String password;
    
    @OneirosEncrypted
    private String ssn;
}
```

### 5. Use OMIT for Privacy

✅ **Good** - Exclude sensitive fields from API responses
```java
OneirosQuery.select(User.class)
    .omit("password", "ssn", "credit_card")
    .execute(client);
```

### 6. Set Query Timeouts

✅ **Good** - Prevent long-running queries
```java
OneirosQuery.select(User.class)
    .timeout(Duration.ofSeconds(5))
    .execute(client);
```

### 7. Use Pagination

✅ **Good** - Paginate large result sets
```java
OneirosQuery.select(User.class)
    .orderBy("created_at", "DESC")
    .limit(50)
    .start(pageNumber * 50)
    .execute(client);
```

---

## 🔧 Troubleshooting

### Connection Issues

#### ❌ Problem: `WebSocketException: Connection refused` or No Connection on Startup

**Symptoms:**
```
❌ Oneiros connection failed: Connection refused
⚠️ Oneiros is NOT connected to SurrealDB!
```

**Solutions:**

1️⃣ **Check if SurrealDB is running:**
```bash
# Start SurrealDB
surreal start --user root --pass root

# Or with Docker
docker run --rm -p 8000:8000 surrealdb/surrealdb:latest start
```

2️⃣ **Verify configuration in `application.yml`:**
```yaml
oneiros:
  url: "ws://127.0.0.1:8000/rpc"  # Check port and protocol (ws://)
  username: "root"                 # Must match SurrealDB credentials
  password: "root"
  namespace: "my_namespace"        # Must exist or be creatable
  database: "my_database"
  auto-connect: true               # Enable for immediate connection
```

3️⃣ **Check Spring Boot auto-configuration:**
```java
// If using local JAR, ensure @ComponentScan includes Oneiros:
@SpringBootApplication
@ComponentScan(basePackages = {
    "your.package",
    "io.oneiros"  // Add this!
})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

4️⃣ **Enable debug logging:**
```yaml
logging:
  level:
    io.oneiros: DEBUG
    reactor.netty: DEBUG
```

5️⃣ **Check actuator health endpoint:**
```bash
curl http://localhost:8080/actuator/health
```

#### ❌ Problem: Connection Works in Tests but Not in Application

**Cause:** Configuration not loaded from `application.yml`

**Solution:**
```java
// Ensure @EnableConfigurationProperties is present:
@SpringBootApplication
@EnableConfigurationProperties(OneirosProperties.class)
public class Application { }
```

#### ❌ Problem: "Session not available after connect"

**Cause:** Connection pool exhausted or all connections unhealthy

**Solution:**
```yaml
oneiros:
  connection-pool:
    enabled: true
    size: 10          # Increase pool size
    health-check-interval-seconds: 30
```

### Encryption Errors

**Problem**: `EncryptionException: Invalid key length`

**Solution**: Ensure encryption key is at least 16 characters:
```yaml
oneiros:
  security:
    key: "MySecretKey123456789"  # Must be >= 16 characters
```

### Query Timeout

**Problem**: `TimeoutException: Query exceeded timeout`

**Solution**: Increase timeout:
```java
OneirosQuery.select(User.class)
    .timeout(Duration.ofSeconds(10))
    .execute(client);
```

### Null Pointer Exception

**Problem**: `NullPointerException` when accessing query results

**Solution**: Use reactive operators properly:
```java
// Correct
userRepository.findById(userId)
    .switchIfEmpty(Mono.error(new UserNotFoundException()))
    .map(User::getName)
    .subscribe(name -> System.out.println(name));
```

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- [SurrealDB](https://surrealdb.com/) - The amazing database
- [Project Reactor](https://projectreactor.io/) - Reactive programming support
- [Spring Boot](https://spring.io/projects/spring-boot) - Auto-configuration framework

---

<div align="center">

**Made with ❤️ by the Oneiros Team**

[⬆ Back to Top](#-oneiros---reactive-surrealdb-client-for-java)

</div>
