# Oneiros Statement API - Complete Reference

## 🎯 Overview

The Oneiros Statement API provides a **complete, type-safe, fluent interface** for all SurrealQL statements. Each statement is represented by its own class implementing the `Statement<T>` interface.

## 📦 Architecture

```
Statement<T> (interface)
    ├── SelectStatement<T>      - SELECT queries
    ├── CreateStatement<T>      - CREATE records
    ├── UpdateStatement<T>      - UPDATE records
    ├── DeleteStatement<T>      - DELETE records
    ├── UpsertStatement<T>      - UPSERT (insert-or-update)
    ├── InsertStatement<T>      - INSERT with ON DUPLICATE KEY
    ├── RelateStatement         - RELATE (graph edges)
    ├── TransactionStatement    - BEGIN/COMMIT/CANCEL
    ├── IfStatement             - IF/ELSE conditionals
    ├── ForStatement            - FOR loops
    ├── LetStatement            - LET variables
    ├── ThrowStatement          - THROW errors
    ├── ReturnStatement         - RETURN values
    ├── SleepStatement          - SLEEP delays
    ├── BreakStatement          - BREAK from loops
    └── ContinueStatement       - CONTINUE loop iteration
```

## 🚀 Quick Start

### Basic CRUD Operations

```java
// CREATE
CreateStatement.table(User.class)
    .set("name", "Alice")
    .set("email", "alice@example.com")
    .returnAfter()
    .execute(client)
    .subscribe(user -> log.info("Created: {}", user));

// SELECT
SelectStatement.from(User.class)
    .fields("name", "email")
    .where("age > 18")
    .orderBy("name", "ASC")
    .limit(10)
    .execute(client)
    .subscribe(user -> log.info("Found: {}", user));

// UPDATE
UpdateStatement.table(User.class)
    .set("verified", true)
    .where("email = 'alice@example.com'")
    .returnAfter()
    .execute(client);

// DELETE
DeleteStatement.from(User.class)
    .where("age < 18")
    .returnBefore()
    .execute(client);

// UPSERT
UpsertStatement.table(User.class)
    .set("name", "Bob")
    .set("email", "bob@example.com")
    .where("email = 'bob@example.com'")
    .execute(client);
```

## 📝 Detailed Statement Reference

### SELECT Statement

**Supports all SurrealQL SELECT clauses:**
- Fields projection
- WHERE conditions
- ORDER BY
- LIMIT / START
- FETCH (graph traversal)
- OMIT (exclude fields)
- TIMEOUT
- PARALLEL
- EXPLAIN

```java
SelectStatement.from(User.class)
    .fields("id", "name", "email")
    .where("age >= 18")
    .and("verified = true")
    .orderBy("created_at", "DESC")
    .limit(50)
    .start(0)
    .fetch("profile", "permissions")
    .omit("password")
    .timeout("5s")
    .parallel()
    .execute(client);
```

### CREATE Statement

**Features:**
- SET field = value
- CONTENT object
- RETURN clauses (NONE, BEFORE, AFTER, DIFF)
- TIMEOUT

```java
// Using SET
CreateStatement.table(User.class)
    .set("name", "Alice")
    .set("age", 25)
    .returnAfter()
    .execute(client);

// Using CONTENT
CreateStatement.table(User.class)
    .content("{ name: 'Alice', age: 25 }")
    .execute(client);

// Specific ID
CreateStatement.record(User.class, "alice")
    .set("name", "Alice")
    .execute(client);
```

### UPDATE Statement

**Features:**
- SET field = value
- Raw expressions (e.g., `balance += 100`)
- MERGE partial updates
- CONTENT full replacement
- WHERE conditions
- RETURN clauses

```java
// Simple update
UpdateStatement.table(User.class)
    .set("verified", true)
    .where("id = user:alice")
    .execute(client);

// With raw expression
UpdateStatement.table(Account.class)
    .setRaw("balance += 100")
    .where("user_id = user:alice")
    .execute(client);

// MERGE
UpdateStatement.table(User.class)
    .merge("{ settings: { theme: 'dark' } }")
    .where("id = user:alice")
    .execute(client);
```

### UPSERT Statement

**Insert-or-update in one operation:**

```java
// Will insert if not exists, update if exists
UpsertStatement.table(User.class)
    .set("name", "Bob")
    .set("email", "bob@example.com")
    .where("email = 'bob@example.com'")
    .execute(client);

// With specific ID
UpsertStatement.record(User.class, "bob")
    .set("name", "Bob")
    .set("last_login", "time::now()")
    .execute(client);
```

### INSERT Statement

**Features:**
- VALUES syntax
- CONTENT syntax
- ON DUPLICATE KEY UPDATE
- IGNORE clause
- RELATION inserts

```java
// Basic insert
InsertStatement.into(User.class)
    .fields("name", "email")
    .values("Charlie", "charlie@example.com")
    .execute(client);

// With duplicate key handling
InsertStatement.into(User.class)
    .fields("name", "email")
    .values("Alice", "alice@example.com")
    .onDuplicateKeyUpdate()
        .set("updated_at", "time::now()")
        .set("login_count", "login_count + 1")
    .end()
    .execute(client);

// Ignore errors
InsertStatement.into(User.class)
    .ignore()
    .fields("name", "email")
    .values("Alice", "alice@example.com")
    .execute(client);
```

### RELATE Statement

**Create graph relationships:**

```java
RelateStatement.from("person:alice")
    .to("person:bob")
    .via("knows")
    .set("since", "2020-01-01")
    .set("strength", 8)
    .execute(client);

// Multiple relationships
RelateStatement.from("[person:alice, person:bob]")
    .to("company:surrealdb")
    .via("works_at")
    .set("position", "Engineer")
    .execute(client);
```

### Transaction Statement

**Atomic operations:**

```java
TransactionStatement.begin()
    .add(UpdateStatement.table(Account.class)
        .setRaw("balance -= 100")
        .where("id = account:alice"))
    .add(UpdateStatement.table(Account.class)
        .setRaw("balance += 100")
        .where("id = account:bob"))
    .returnValue("{ success: true, transferred: 100 }")
    .commit(client);

// Rollback
TransactionStatement.begin()
    .add(CreateStatement.table(User.class).set("name", "Test"))
    .cancel()
    .rollback(client);
```

### IF/ELSE Statement

**Conditional logic:**

```java
IfStatement.condition("user.role = 'ADMIN'")
    .then(UpdateStatement.table(User.class)
        .set("permissions", "full"))
    .elseIf("user.role = 'USER'")
    .then(UpdateStatement.table(User.class)
        .set("permissions", "limited"))
    .elseBlock()
    .then(ThrowStatement.error("Invalid role"))
    .build()
    .execute(client);
```

### FOR Loop Statement

**Iterate over arrays and ranges:**

```java
// Loop over query results
ForStatement.forEach("$person", "(SELECT * FROM person WHERE age > 18)")
    .add(UpdateStatement.record(User.class, "$person.id")
        .set("can_vote", true))
    .execute(client);

// Loop over range
ForStatement.forEach("$i", "0..100")
    .add(CreateStatement.table(User.class)
        .set("name", "'User ' + $i"))
    .execute(client);

// With CONTINUE/BREAK
ForStatement.forEach("$user", "(SELECT * FROM user)")
    .addRaw("IF $user.age < 18 { CONTINUE }")
    .add(UpdateStatement.record(User.class, "$user.id")
        .set("verified", true))
    .execute(client);
```

### LET Statement

**Variable assignment:**

```java
TransactionStatement.begin()
    .add(LetStatement.variable("user_id", "user:alice"))
    .add(LetStatement.variable("amount", "100"))
    .add(UpdateStatement.table(Account.class)
        .setRaw("balance += $amount")
        .where("user_id = $user_id"))
    .commit(client);
```

### THROW Statement

**Error handling:**

```java
IfStatement.condition("account.balance < $amount")
    .then(ThrowStatement.error("Insufficient funds"))
    .elseBlock()
    .then(UpdateStatement.table(Account.class)
        .setRaw("balance -= $amount"))
    .build()
    .execute(client);
```

### Utility Statements

```java
// RETURN
ReturnStatement.value("{ success: true }").execute(client);

// SLEEP
SleepStatement.duration("100ms").execute(client);
SleepStatement.duration("5s").execute(client);

// BREAK (in loops)
BreakStatement.exit();

// CONTINUE (in loops)
ContinueStatement.skip();
```

## 🔧 Advanced Patterns

### Complex Transaction with Error Handling

```java
TransactionStatement.begin()
    .add(LetStatement.variable("from_account", "account:alice"))
    .add(LetStatement.variable("to_account", "account:bob"))
    .add(LetStatement.variable("amount", "100"))
    
    // Check balance
    .addRaw("LET $balance = (SELECT balance FROM $from_account).balance")
    
    // Conditional transfer
    .add(IfStatement.condition("$balance < $amount")
        .then(ThrowStatement.error("Insufficient funds"))
        .elseBlock()
        .then(UpdateStatement.table(Account.class)
            .setRaw("balance -= $amount")
            .where("id = $from_account"))
        .then(UpdateStatement.table(Account.class)
            .setRaw("balance += $amount")
            .where("id = $to_account"))
        .build())
    
    .returnValue("{ success: true, amount: $amount }")
    .commit(client);
```

### Bulk Operations with FOR Loop

```java
ForStatement.forEach("$user", "(SELECT * FROM user WHERE verified = false)")
    .add(IfStatement.condition("time::now() - $user.created_at > 7d")
        .then(DeleteStatement.record(User.class, "$user.id"))
        .build())
    .execute(client);
```

### Graph Traversal with Conditions

```java
SelectStatement.from(User.class)
    .fields("id", "name")
    .where("->knows->person->(knows WHERE influencer = true)")
    .fetch("->knows->person.profile")
    .timeout("5s")
    .execute(client);
```

## 🎨 Design Benefits

### ✅ Type Safety
All statements are strongly typed with generics.

### ✅ Fluent API
Chainable methods for readable code.

### ✅ Reactive
Built on Project Reactor for non-blocking operations.

### ✅ Composable
Statements can be nested and combined.

### ✅ Complete
Covers 100% of SurrealQL functionality.

### ✅ Discoverable
IDE autocomplete shows available methods.

## 📚 Migration from TransactionBuilder

**Old Way (TransactionBuilder):**
```java
TransactionBuilder.begin()
    .create("user", Map.of("name", "Alice"))
    .update("account")
        .set("balance", 100)
        .where("user = 'alice'")
    .commit(client);
```

**New Way (Statement API):**
```java
TransactionStatement.begin()
    .add(CreateStatement.table(User.class)
        .set("name", "Alice"))
    .add(UpdateStatement.table(Account.class)
        .set("balance", 100)
        .where("user = 'alice'"))
    .commit(client);
```

## 🔍 See Also

- [OneirosQuery](./OneirosQuery.java) - Query builder for SELECT statements
- [Statement Interface](./Statement.java) - Base interface for all statements
- [StatementAPIDemo](./StatementAPIDemo.java) - Comprehensive examples

---

**Version:** 1.0.0  
**License:** MIT  
**Author:** Oneiros Team
