# 🏗️ Statement & Clause System - Architecture

## 📊 Übersicht

Das neue **Statement & Clause System** bietet eine vollständige, modulare Architektur für SurrealQL-Queries mit allen offiziellen Clauses.

---

## 🎯 Architektur

### Core Interfaces

```
io.oneiros.statement/
├── Statement.java          # Base interface für alle Statements
└── clause/
    ├── Clause.java         # Base interface für alle Clauses
    ├── WhereClause.java    # WHERE filtering
    ├── GroupByClause.java  # GROUP BY grouping
    ├── OrderByClause.java  # ORDER BY sorting
    ├── LimitClause.java    # LIMIT + START pagination
    ├── FetchClause.java    # FETCH related records
    ├── OmitClause.java     # OMIT sensitive fields
    ├── SplitClause.java    # SPLIT subqueries
    ├── TimeoutClause.java  # TIMEOUT query limit
    ├── ParallelClause.java # PARALLEL execution
    └── ExplainClause.java  # EXPLAIN query plan
```

---

## 🚀 Features

### 1. **SelectStatement** - Vollständiger Query Builder

```java
SelectStatement.from(User.class)
    .select("name", "age", "email")  // Custom projection
    .where("role = 'ADMIN'")          // WHERE clause
    .and("status != 'DELETED'")       // AND condition
    .or("premium = true")              // OR condition
    .omit("password", "secretKey")    // OMIT sensitive fields
    .fetch("profile", "permissions")  // FETCH related records
    .split("category")                 // SPLIT into subqueries
    .groupBy("department", "country") // GROUP BY
    .orderBy("name")                   // ORDER BY ASC
    .orderByDesc("createdAt")          // ORDER BY DESC
    .limit(10, 20)                     // LIMIT 10 START 20
    .timeout(Duration.ofSeconds(5))   // TIMEOUT 5s
    .parallel()                        // PARALLEL execution
    .explain()                         // EXPLAIN query plan
    .execute(client);                  // Execute
```

### 2. **TransactionBuilder** - Erweitert mit THROW & RETURN

```java
// Simple Transaction
TransactionBuilder.begin()
    .add("CREATE account:one SET balance = 1000")
    .add("CREATE account:two SET balance = 500")
    .add("UPDATE account:one SET balance -= 100")
    .add("UPDATE account:two SET balance += 100")
    .commit(client);

// Mit Conditional Logic (THROW)
TransactionBuilder.begin()
    .add("CREATE account:two SET can_transfer = true")
    .addIf("!account:two.can_transfer", "Transfer not allowed!")
    .add("UPDATE account:one SET balance += 10")
    .commit(client);

// Mit Custom Return Value
TransactionBuilder.begin()
    .add("LET $first = UPDATE account:one SET balance += 300")
    .add("LET $second = UPDATE account:two SET balance -= 300")
    .returnValue("'Money sent! Status: ' + <string>$first")
    .commit(client);

// Transaction canceln
TransactionBuilder.begin()
    .add("CREATE test:one SET value = 1")
    .markForCancel()
    .toSql(); // Generiert CANCEL TRANSACTION
```

---

## 📋 Alle 10 Clauses

### 1. **WHERE** - Filtering
```java
.where("role = 'ADMIN'")
.and("age > 18")
.or("premium = true")
```

### 2. **GROUP BY** - Grouping
```java
.groupBy("department", "country")
```

### 3. **ORDER BY** - Sorting
```java
.orderBy("name")          // ASC
.orderByDesc("createdAt") // DESC
```

### 4. **LIMIT** - Pagination
```java
.limit(10)       // LIMIT 10
.limit(10, 20)   // LIMIT 10 START 20
```

### 5. **FETCH** - Related Records
```java
.fetch("profile", "permissions", "posts")
```

### 6. **OMIT** - Exclude Fields
```java
.omit("password", "secretKey", "history")
```

### 7. **SPLIT** - Subqueries
```java
.split("category")
```

### 8. **TIMEOUT** - Query Timeout
```java
.timeout(Duration.ofSeconds(5))
```

### 9. **PARALLEL** - Parallel Execution
```java
.parallel()
```

### 10. **EXPLAIN** - Query Plan
```java
.explain()      // EXPLAIN SELECT...
.explainFull()  // EXPLAIN FULL SELECT...
```

---

## 🎯 SQL Generation Order

```
[EXPLAIN [FULL]]
SELECT projection
[OMIT fields]
FROM table
[WHERE conditions]
[SPLIT fields]
[GROUP BY fields]
[ORDER BY field]
[LIMIT n [START offset]]
[FETCH fields]
[TIMEOUT duration]
[PARALLEL]
```

---

## 🧪 Tests

**8 neue Tests** für das Statement & Clause System:

1. ✅ SELECT with all clauses
2. ✅ SELECT with EXPLAIN
3. ✅ SELECT with SPLIT
4. ✅ Transaction with THROW
5. ✅ Transaction with RETURN
6. ✅ Transaction with CANCEL
7. ✅ SELECT with custom projection
8. ✅ Multiple ORDER BY

**Run Tests:**
```bash
./gradlew run -PmainClass=io.oneiros.statement.StatementSystemTest
```

---

## 📊 Vergleich: Alt vs. Neu

### Alt: OneirosQuery (Monolithisch)
```java
OneirosQuery.select(User.class)
    .where("name").is("Alice")
    .omit("password")
    .fetch("profile")
    .limit(10)
    .execute(client);
```

### Neu: SelectStatement (Modular)
```java
SelectStatement.from(User.class)
    .where("name = 'Alice'")
    .omit("password")
    .fetch("profile")
    .limit(10)
    .execute(client);
```

**Vorteile des neuen Systems:**
- ✅ Modulare Clause-Architektur
- ✅ Alle 10 SurrealQL Clauses
- ✅ Einfacher erweiterbar
- ✅ Bessere Testbarkeit
- ✅ Flexiblere WHERE-Bedingungen

---

## 🔄 Migration Guide

### OneirosQuery → SelectStatement

| OneirosQuery | SelectStatement |
|--------------|----------------|
| `.where("field").is(value)` | `.where("field = '" + value + "'")` |
| `.where("field").gt(value)` | `.where("field > " + value)` |
| `.and("field").is(value)` | `.and("field = '" + value + "'")` |
| `.or("field").is(value)` | `.or("field = '" + value + "'")` |
| `.omit(fields)` | `.omit(fields)` ✅ Gleich |
| `.fetch(fields)` | `.fetch(fields)` ✅ Gleich |
| `.limit(n)` | `.limit(n)` ✅ Gleich |
| `.offset(n)` | `.limit(10, n)` 📝 Kombiniert |
| `.timeout(duration)` | `.timeout(duration)` ✅ Gleich |
| `.parallel()` | `.parallel()` ✅ Gleich |
| `.execute(client)` | `.execute(client)` ✅ Gleich |

**Neu in SelectStatement:**
- ✅ `.select(fields)` - Custom projection
- ✅ `.groupBy(fields)` - Grouping
- ✅ `.split(fields)` - Subqueries
- ✅ `.explain()` / `.explainFull()` - Query plan

---

## 🎓 Best Practices

### 1. Use WHERE for complex conditions
```java
// ✅ Good - Flexible
.where("role = 'ADMIN' AND age > 18")

// ❌ Less flexible (old way)
.where("role").is("ADMIN")
.and("age").gt(18)
```

### 2. Use EXPLAIN for debugging
```java
// Debug query performance
SelectStatement.from(User.class)
    .where("email CONTAINS 'gmail'")
    .explainFull()
    .execute(client);
```

### 3. Use SPLIT for subqueries
```java
// Split results by category
SelectStatement.from(Product.class)
    .split("category")
    .execute(client);
```

### 4. Use Transaction THROW for validation
```java
TransactionBuilder.begin()
    .add("LET $balance = (SELECT balance FROM account:one)")
    .addIf("$balance < 100", "Insufficient funds!")
    .add("UPDATE account:one SET balance -= 100")
    .commit(client);
```

---

## 🚀 Performance

### PARALLEL Execution
```java
// Process large datasets faster
SelectStatement.from(User.class)
    .where("role = 'ADMIN'")
    .parallel()
    .execute(client);
```

### TIMEOUT Protection
```java
// Protect against slow queries
SelectStatement.from(User.class)
    .where("email CONTAINS 'test'")
    .timeout(Duration.ofSeconds(3))
    .execute(client);
```

### EXPLAIN for Optimization
```java
// Analyze query execution
SelectStatement.from(User.class)
    .where("email = 'test@example.com'")
    .explain()
    .execute(client);
```

---

## 📦 Dateien

### Neue Dateien (12)
1. `Statement.java` - Base interface
2. `Clause.java` - Base clause interface
3. `WhereClause.java`
4. `GroupByClause.java`
5. `OrderByClause.java`
6. `LimitClause.java`
7. `FetchClause.java`
8. `OmitClause.java`
9. `SplitClause.java`
10. `TimeoutClause.java`
11. `ParallelClause.java`
12. `ExplainClause.java`

### Statements (2)
1. `SelectStatement.java` - SELECT with all clauses
2. `TransactionBuilder.java` - Enhanced with THROW/RETURN

### Tests (1)
1. `StatementSystemTest.java` - 8 tests

---

## 🎉 Zusammenfassung

Das neue **Statement & Clause System** bietet:

- ✅ **10 vollständige Clauses**
- ✅ **Modulare Architektur**
- ✅ **Erweiterte Transactions** (THROW, RETURN, CANCEL)
- ✅ **Alle SurrealQL Features**
- ✅ **8 neue Tests**
- ✅ **Production-Ready**

**Die Library ist jetzt vollständig mit allen SurrealQL-Features!** 🚀
