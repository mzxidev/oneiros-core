# Statement System Implementation Plan

## Overview
Implement full SurrealQL statement support in TransactionBuilder with fluent API.

## Statements to Implement

### 1. Core CRUD (Already Done ✅)
- [x] CREATE
- [x] UPDATE  
- [x] DELETE
- [x] SELECT
- [x] INSERT
- [x] UPSERT

### 2. Transaction Control (Partially Done)
- [x] BEGIN
- [x] COMMIT
- [x] CANCEL
- [x] THROW
- [x] RETURN
- [ ] LET (expand)

### 3. Flow Control
- [x] IF/ELSE (via IfConditionBuilder)
- [ ] FOR loops
- [ ] CONTINUE
- [ ] BREAK
- [ ] SLEEP

### 4. Graph Relations
- [ ] RELATE
- [ ] FETCH (in queries)

### 5. Schema & Admin
- [ ] DEFINE (TABLE, FIELD, INDEX, etc.)
- [ ] ALTER
- [ ] REMOVE
- [ ] REBUILD
- [ ] INFO
- [ ] SHOW
- [ ] USE

### 6. Access & Security
- [ ] ACCESS (GRANT, SHOW, REVOKE, PURGE)

### 7. Live Queries
- [ ] LIVE SELECT
- [ ] KILL

## Implementation Strategy

### Phase 1: Core Statements (Priority: HIGH)
1. **FOR loops** - Essential for batch operations
2. **RELATE** - Graph relations are key feature
3. **LET** - Variable management (expand current)
4. **SLEEP** - Useful for testing/throttling

### Phase 2: Schema Management (Priority: MEDIUM)
5. **DEFINE** - Schema definitions
6. **ALTER** - Schema modifications
7. **REMOVE** - Resource cleanup
8. **INFO** - Introspection

### Phase 3: Advanced Features (Priority: LOW)
9. **ACCESS** - Security/token management
10. **LIVE SELECT** - Real-time queries
11. **SHOW CHANGES** - Change feeds

## API Design Principles

1. **Fluent Interface**: Every method returns builder for chaining
2. **Type Safety**: Use Java types where possible
3. **Clear Naming**: Method names match SurrealQL keywords
4. **Nested Builders**: Complex statements get own builder class
5. **Documentation**: JavaDoc for all public methods

## Example Usage Goals

```java
// FOR loop
.forEach("person", "people")
    .create("log").set("processed", "$person.id").endCreate()
.endFor()

// RELATE
.relate("person:tobie").to("person:jaime").via("knows")
    .set("since", "2020-01-01")
.endRelate()

// DEFINE
.defineTable("user")
    .schemafull()
    .field("name").type("string").required()
    .field("email").type("string").unique()
.endDefine()
```

## File Structure

```
transaction/
├── TransactionBuilder.java         (main)
├── IfConditionBuilder.java        (✅ done)
├── ForLoopBuilder.java            (new)
├── RelateStatementBuilder.java    (new)
├── DefineStatementBuilder.java    (new)
├── AccessStatementBuilder.java    (new)
└── FluentTransactionTest.java     (expand)
```

## Testing Strategy

- Add ~5 tests per new statement type
- Test success cases
- Test error handling
- Test complex nesting
- Target: 95%+ test coverage
