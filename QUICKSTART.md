# 🚀 Oneiros Quick Start

## Installation & Start (ohne Docker)

### 1. SurrealDB installieren:
```bash
curl -sSf https://install.surrealdb.com | sh
```

### 2. SurrealDB starten:
```bash
surreal start --user root --pass root memory
```

### 3. Oneiros Anwendung starten:
```bash
./gradlew bootRun
```

---

## 🎯 Verwende den QueryBuilder

### Beispiel 1: Einfache Suche
```java
@Autowired
private OneirosClient client;

// Alle aktiven User
OneirosQuery.select(User.class)
    .where("status").is("ACTIVE")
    .fetch(client)
    .subscribe(users -> System.out.println(users));
```

### Beispiel 2: Komplexe Filterung
```java
// User mit ADMIN oder MODERATOR Rolle, nicht gelöscht
OneirosQuery.select(User.class)
    .where("role").in("ADMIN", "MODERATOR")
    .and("deletedAt").isNull()
    .orderByDesc("createdAt")
    .limit(10)
    .fetch(client);
```

### Beispiel 3: Suche mit OR
```java
// User die "admin" im Namen oder Email haben
OneirosQuery.select(User.class)
    .where("username").like("admin")
    .or("email").like("admin")
    .fetch(client);
```

### Beispiel 4: Pagination
```java
// Seite 3 (20 pro Seite)
OneirosQuery.select(User.class)
    .orderBy("username")
    .offset(40)  // Skip 2 Seiten
    .limit(20)   // 20 pro Seite
    .fetch(client);
```

---

## 📡 REST API Endpoints

### User erstellen:
```bash
curl -X POST "http://localhost:8080/api/users?username=Alice&email=alice@example.com"
```

### Alle User abrufen:
```bash
curl "http://localhost:8080/api/users"
```

### Nach Username suchen (LIKE):
```bash
curl "http://localhost:8080/api/users/search?username=Alice"
```

### Nach Email filtern (exakt):
```bash
curl "http://localhost:8080/api/users/filter?email=alice@example.com"
```

### Top 5 User (sortiert):
```bash
curl "http://localhost:8080/api/users/top?limit=5"
```

### User by ID:
```bash
curl "http://localhost:8080/api/users/users:abc123"
```

### User löschen:
```bash
curl -X DELETE "http://localhost:8080/api/users/users:abc123"
```

### User aktualisieren:
```bash
curl -X PUT "http://localhost:8080/api/users/users:abc123?username=NewName&email=new@example.com"
```

---

## 🔥 QueryBuilder Cheat Sheet

| Methode | SQL | Beispiel |
|---------|-----|----------|
| `.is(val)` | `= val` | `.where("age").is(18)` |
| `.gt(val)` | `> val` | `.where("age").gt(18)` |
| `.lt(val)` | `< val` | `.where("age").lt(65)` |
| `.gte(val)` | `>= val` | `.where("age").gte(18)` |
| `.lte(val)` | `<= val` | `.where("age").lte(65)` |
| `.notEquals(val)` | `!= val` | `.where("status").notEquals("DELETED")` |
| `.like(pattern)` | `CONTAINS pattern` | `.where("email").like("@gmail")` |
| `.in(vals...)` | `IN [vals]` | `.where("role").in("ADMIN", "USER")` |
| `.between(min,max)` | `>= min AND <= max` | `.where("age").between(18, 65)` |
| `.isNull()` | `IS NULL` | `.where("deletedAt").isNull()` |
| `.isNotNull()` | `IS NOT NULL` | `.where("email").isNotNull()` |
| `.and(field)` | `AND` | `.where("x").is(1).and("y").is(2)` |
| `.or(field)` | `OR` | `.where("x").is(1).or("y").is(2)` |
| `.orderBy(field)` | `ORDER BY ASC` | `.orderBy("createdAt")` |
| `.orderByDesc(field)` | `ORDER BY DESC` | `.orderByDesc("createdAt")` |
| `.limit(n)` | `LIMIT n` | `.limit(10)` |
| `.offset(n)` | `START n` | `.offset(20)` |
| `.fetch(client)` | Ausführen → `Flux<T>` | `.fetch(client)` |
| `.fetchOne(client)` | Ausführen → `Mono<T>` | `.fetchOne(client)` |
| `.toSql()` | Debug SQL String | `.toSql()` |

---

## 🧪 Tests ausführen

```bash
# Alle Tests
./gradlew test

# Nur QueryBuilder Tests
./gradlew test --tests QueryBuilderIntegrationTest

# Mit Testcontainers (braucht Docker!)
./gradlew test
```

---

## 🔒 Security Features

```java
// Verschlüsselung automatisch mit @OneirosEncrypted
@Data
@OneirosEntity("users")
public class User {
    @OneirosID
    private String id;
    
    private String username;
    
    @OneirosEncrypted  // <- Wird verschlüsselt!
    private String email;
}
```

Konfiguration in `application.yml`:
```yaml
oneiros:
  security:
    enabled: true
    key: "SuperSecretKey123456"  # Min. 16 Zeichen!
```

---

## 💡 Tipps

1. **SQL Debug:** Nutze `.toSql()` um generierten SQL zu sehen
2. **Lazy Connection:** Anwendung startet auch wenn DB noch nicht läuft
3. **Circuit Breaker:** Automatischer Schutz bei DB-Ausfällen
4. **Cache:** In `application.yml` konfigurierbar
5. **OneID:** IDs werden automatisch generiert (Format: `table:uuid`)

---

## 📖 Weitere Dokumentation

- [CHANGELOG.md](CHANGELOG.md) - Alle Änderungen
- [README.md](README.md) - Installation & Setup
- SurrealDB Docs: https://surrealdb.com/docs
