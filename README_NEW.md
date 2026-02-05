# 🌙 Oneiros - Reactive SurrealDB Library for Java

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![SurrealDB](https://img.shields.io/badge/SurrealDB-2.0%2B-purple.svg)](https://surrealdb.com)
[![Tests](https://img.shields.io/badge/Tests-33%2F33-success.svg)](src/main/java/io/oneiros/QueryBuilderTestRunner.java)

**Oneiros** ist eine moderne, reaktive Java-Library für SurrealDB mit einem eleganten Fluent QueryBuilder, Transaction Support und vollständiger Spring Boot Integration.

---

## ✨ Features

### 🔍 **QueryBuilder (35+ Features)**
- 🎯 Fluent API für elegante Queries
- 🔗 Graph Traversal mit FETCH
- 🚀 Performance mit PARALLEL & TIMEOUT
- 🔒 Security mit OMIT für sensible Daten
- 📊 Pagination mit LIMIT & OFFSET

### 💰 **Transaction Support**
- ⚛️ ACID Guarantees mit BEGIN/COMMIT
- 🔄 Atomic Operations
- 🏦 Bank-Safe für finanzielle Operationen

### 🔌 **Reactive & Spring Boot**
- ⚡ Non-Blocking mit Project Reactor
- 🌊 Native WebSocket zu SurrealDB
- 🔁 Resilience mit Circuit Breaker
- 🔧 Auto-Configuration

---

## 🚀 Quick Start

### 1. Add Dependency

```gradle
dependencies {
    implementation 'io.oneiros:oneiros-core:1.0-SNAPSHOT'
}
```

### 2. Configure

```yaml
oneiros:
  url: ws://localhost:8000/rpc
  namespace: marketplace
  database: secret_db
  username: root
  password: root
```

### 3. Use

```java
@Autowired
private OneirosClient client;

// Simple Query
OneirosQuery.select(User.class)
    .where("username").is("alice")
    .execute(client);

// Complex Query
OneirosQuery.select(User.class)
    .where("role").in("ADMIN", "MODERATOR")
    .omit("password")
    .fetch("profile")
    .limit(10)
    .execute(client);

// Transaction
TransactionBuilder.begin()
    .add("CREATE account:one SET balance = 1000")
    .add("UPDATE account:one SET balance -= 100")
    .commit(client);
```

---

## 📖 Documentation

- [**FEATURES.md**](FEATURES.md) - Complete Feature List
- [**QUICKSTART.md**](QUICKSTART.md) - Getting Started
- [**CHANGELOG.md**](CHANGELOG.md) - What's New

---

## 🎯 API Overview

### QueryBuilder (30+ Methods)

```java
OneirosQuery.select(User.class)
    // Filtering
    .where("field").is(value)
    .where("field").gt(value)      // >, <, >=, <=
    .where("field").in(values...)
    .where("field").like(pattern)
    .where("field").between(min, max)
    .where("field").isNull()
    
    // Logic
    .and("field").or("field")
    
    // Field Selection
    .omit("password")
    .fetch("profile", "permissions")
    
    // Sorting & Pagination
    .orderByDesc("createdAt")
    .limit(10).offset(20)
    
    // Performance
    .timeout(Duration.ofSeconds(5))
    .parallel()
    
    // Execute
    .execute(client);
```

### TransactionBuilder

```java
TransactionBuilder.begin()
    .add("CREATE ...")
    .addAll("UPDATE ...", "RELATE ...")
    .commit(client);
```

---

## 🧪 Tests

```bash
./gradlew test
# Or: ./gradlew run -PmainClass=io.oneiros.QueryBuilderTestRunner
```

**33 Tests - 100% Passing ✅**

---

## 📊 Examples

### User Search
```java
OneirosQuery.select(User.class)
    .where("username").like("alice")
    .and("active").is(true)
    .omit("password")
    .execute(client);
```

### Bank Transfer
```java
TransactionBuilder.begin()
    .add("UPDATE account:sender SET balance -= 100")
    .add("UPDATE account:receiver SET balance += 100")
    .commit(client);
```

### Graph Relations
```java
OneirosQuery.select(Person.class)
    .where("name").is("Alice")
    .fetch("->knows->person")
    .fetchOne(client);
```

---

## 🏗️ Architecture

```
io.oneiros/
├── query/OneirosQuery              # QueryBuilder
├── transaction/TransactionBuilder  # Transactions
├── client/OneirosClient            # WebSocket Client
├── core/SimpleOneirosRepository    # Repository
└── config/OneirosAutoConfiguration # Auto-Config
```

---

## 🙏 Built With

- [SurrealDB](https://surrealdb.com) - Database
- [Spring Boot](https://spring.io) - Framework
- [Project Reactor](https://projectreactor.io) - Reactive
- [Resilience4j](https://resilience4j.readme.io) - Fault Tolerance

---

**⭐ Star this repo if you find it useful!**
