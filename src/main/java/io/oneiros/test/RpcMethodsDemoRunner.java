package io.oneiros.test;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Comprehensive demo of ALL SurrealDB RPC methods supported by OneirosClient.
 * This demo showcases the complete RPC protocol implementation.
 *
 * Usage:
 * 1. Start SurrealDB: surreal start --user root --pass root
 * 2. Run this demo class
 *
 * Demonstrates:
 * - Connection & Session Management (signin, authenticate, use, reset, ping, version)
 * - Variables (let, unset)
 * - Queries (query, graphql, run)
 * - CRUD Operations (select, create, insert, update, upsert, merge, patch, delete)
 * - Graph Relations (relate, insert_relation)
 * - Live Queries (live, listenToLiveQuery, kill)
 */
@Slf4j
public class RpcMethodsDemoRunner {

    public static void main(String[] args) {
        log.info("🚀 SurrealDB RPC Protocol - Complete Method Reference");
        log.info("═══════════════════════════════════════════════════════════");
        log.info("");

        demonstrateConnectionMethods();
        demonstrateVariableMethods();
        demonstrateQueryMethods();
        demonstrateCrudMethods();
        demonstrateGraphMethods();
        demonstrateLiveMethods();

        log.info("");
        log.info("═══════════════════════════════════════════════════════════");
        log.info("✅ RPC Protocol Demo Complete!");
        log.info("");
        log.info("📚 For implementation details, see:");
        log.info("   - OneirosClient.java (interface)");
        log.info("   - OneirosWebsocketClient.java (implementation)");
        log.info("   - Official docs: https://surrealdb.com/docs/surrealdb/integration/rpc");
    }

    private static void demonstrateConnectionMethods() {
        log.info("1️⃣ CONNECTION & SESSION MANAGEMENT");
        log.info("─────────────────────────────────────────────────────────");
        log.info("");

        log.info("📌 authenticate(token)");
        log.info("   Authenticate using a JWT token");
        log.info("   Example: client.authenticate(\"eyJhbGci...\").subscribe()");
        log.info("");

        log.info("📌 signin(credentials)");
        log.info("   Sign in as root/namespace/database/record user");
        log.info("   Example (Root):");
        log.info("     client.signin(Map.of(");
        log.info("       \"user\", \"root\",");
        log.info("       \"pass\", \"root\"");
        log.info("     )).subscribe(token -> ...)");
        log.info("");
        log.info("   Example (Record User):");
        log.info("     client.signin(Map.of(");
        log.info("       \"NS\", \"myapp\",");
        log.info("       \"DB\", \"prod\",");
        log.info("       \"AC\", \"user_access\",");
        log.info("       \"username\", \"alice\",");
        log.info("       \"password\", \"secret123\"");
        log.info("     )).subscribe(token -> ...)");
        log.info("");

        log.info("📌 signup(credentials)");
        log.info("   Register a new user via record access method");
        log.info("   Example:");
        log.info("     client.signup(Map.of(");
        log.info("       \"NS\", \"myapp\",");
        log.info("       \"DB\", \"prod\",");
        log.info("       \"AC\", \"user_access\",");
        log.info("       \"email\", \"alice@example.com\",");
        log.info("       \"password\", \"secret123\"");
        log.info("     )).subscribe(token -> ...)");
        log.info("");

        log.info("📌 use(namespace, database)");
        log.info("   Switch namespace and/or database");
        log.info("   Example: client.use(\"myapp\", \"production\").subscribe()");
        log.info("   Special: use(null, null) unsets both");
        log.info("");

        log.info("📌 info(UserInfo.class)");
        log.info("   Get authenticated record user information");
        log.info("   Example: client.info(UserInfo.class).subscribe(info -> ...)");
        log.info("");

        log.info("📌 invalidate()");
        log.info("   End current session");
        log.info("   Example: client.invalidate().subscribe()");
        log.info("");

        log.info("📌 reset()");
        log.info("   Clear auth, namespace, database, variables, and live queries");
        log.info("   Example: client.reset().subscribe()");
        log.info("");

        log.info("📌 ping()");
        log.info("   Keep connection alive");
        log.info("   Example: client.ping().subscribe()");
        log.info("");

        log.info("📌 version()");
        log.info("   Get SurrealDB version info");
        log.info("   Example: client.version().subscribe(v -> log.info(\"Version: {}\", v.get(\"version\")))");
        log.info("");
    }

    private static void demonstrateVariableMethods() {
        log.info("2️⃣ SESSION VARIABLES");
        log.info("─────────────────────────────────────────────────────────");
        log.info("");

        log.info("📌 let(name, value)");
        log.info("   Define a session variable");
        log.info("   Example: client.let(\"user_id\", \"user:alice\").subscribe()");
        log.info("   Use in query: SELECT * FROM $user_id");
        log.info("");

        log.info("📌 unset(name)");
        log.info("   Remove a session variable");
        log.info("   Example: client.unset(\"user_id\").subscribe()");
        log.info("");
    }

    private static void demonstrateQueryMethods() {
        log.info("3️⃣ QUERIES & FUNCTIONS");
        log.info("─────────────────────────────────────────────────────────");
        log.info("");

        log.info("📌 query(sql, params, resultType)");
        log.info("   Execute SurrealQL query");
        log.info("   Example:");
        log.info("     client.query(");
        log.info("       \"SELECT * FROM users WHERE age > $min_age\",");
        log.info("       Map.of(\"min_age\", 18),");
        log.info("       User.class");
        log.info("     ).collectList().subscribe(users -> ...)");
        log.info("");

        log.info("📌 graphql(query, options)");
        log.info("   Execute GraphQL query");
        log.info("   Example:");
        log.info("     client.graphql(");
        log.info("       Map.of(");
        log.info("         \"query\", \"{ users { id name } }\",");
        log.info("         \"variables\", Map.of(\"limit\", 10)");
        log.info("       ),");
        log.info("       Map.of(\"pretty\", true)");
        log.info("     ).subscribe(result -> ...)");
        log.info("");

        log.info("📌 run(functionName, version, args, resultType)");
        log.info("   Execute built-in/custom function or ML model");
        log.info("   Example (built-in):");
        log.info("     client.run(\"time::now\", null, null, String.class).subscribe(now -> ...)");
        log.info("");
        log.info("   Example (custom function):");
        log.info("     client.run(\"fn::calculate_discount\", null, List.of(100, 15), Double.class)");
        log.info("       .subscribe(discounted -> ...)");
        log.info("");
        log.info("   Example (ML model):");
        log.info("     client.run(\"ml::sentiment_analysis\", \"v1.0\", List.of(\"Great product!\"), String.class)");
        log.info("       .subscribe(sentiment -> ...)");
        log.info("");
    }

    private static void demonstrateCrudMethods() {
        log.info("4️⃣ CRUD OPERATIONS");
        log.info("─────────────────────────────────────────────────────────");
        log.info("");

        log.info("📌 select(thing, resultType)");
        log.info("   Select records from table or specific record");
        log.info("   Example (all):");
        log.info("     client.select(\"users\", User.class).collectList().subscribe(...)");
        log.info("   Example (specific):");
        log.info("     client.select(\"users:alice\", User.class).collectList().subscribe(...)");
        log.info("");
        log.info("   With options (RPC v2):");
        log.info("     client.select(\"users\", Map.of(");
        log.info("       \"where\", \"age > 18\",");
        log.info("       \"limit\", 10");
        log.info("     ), User.class).collectList().subscribe(...)");
        log.info("");

        log.info("📌 create(thing, data, resultType)");
        log.info("   Create a single record");
        log.info("   Example:");
        log.info("     client.create(\"users\", Map.of(");
        log.info("       \"name\", \"Alice\",");
        log.info("       \"email\", \"alice@example.com\"");
        log.info("     ), User.class).subscribe(created -> ...)");
        log.info("");

        log.info("📌 insert(thing, data, resultType)");
        log.info("   Insert one or multiple records");
        log.info("   Example (single):");
        log.info("     client.insert(\"users\", userData, User.class).collectList().subscribe(...)");
        log.info("   Example (bulk):");
        log.info("     client.insert(\"users\", List.of(user1, user2, user3), User.class)");
        log.info("       .collectList().subscribe(...)");
        log.info("");

        log.info("📌 update(thing, data, resultType)");
        log.info("   Replace records (must exist)");
        log.info("   Example:");
        log.info("     client.update(\"users:alice\", Map.of(\"age\", 26), User.class)");
        log.info("       .collectList().subscribe(...)");
        log.info("");

        log.info("📌 upsert(thing, data, resultType)");
        log.info("   Update or insert if not exists");
        log.info("   Example:");
        log.info("     client.upsert(\"users:bob\", userData, User.class)");
        log.info("       .collectList().subscribe(...)");
        log.info("");

        log.info("📌 merge(thing, data, resultType)");
        log.info("   Partial update (merges with existing data)");
        log.info("   Example:");
        log.info("     client.merge(\"users:alice\", Map.of(\"verified\", true), User.class)");
        log.info("       .collectList().subscribe(...)");
        log.info("");

        log.info("📌 patch(thing, patches, returnDiff)");
        log.info("   Apply JSON Patch operations");
        log.info("   Example:");
        log.info("     client.patch(\"users:alice\", List.of(");
        log.info("       Map.of(\"op\", \"replace\", \"path\", \"/age\", \"value\", 27)");
        log.info("     ), false).collectList().subscribe(...)");
        log.info("");

        log.info("📌 delete(thing, resultType)");
        log.info("   Delete records (returns deleted records)");
        log.info("   Example:");
        log.info("     client.delete(\"users:alice\", User.class).collectList().subscribe(...)");
        log.info("");
    }

    private static void demonstrateGraphMethods() {
        log.info("5️⃣ GRAPH RELATIONS");
        log.info("─────────────────────────────────────────────────────────");
        log.info("");

        log.info("📌 relate(in, relation, out, data, resultType)");
        log.info("   Create graph edge between records");
        log.info("   Example (simple):");
        log.info("     client.relate(\"user:alice\", \"follows\", \"user:bob\", null, Relation.class)");
        log.info("       .subscribe(...)");
        log.info("");
        log.info("   Example (with data):");
        log.info("     client.relate(");
        log.info("       \"user:alice\",");
        log.info("       \"purchased\",");
        log.info("       \"product:laptop\",");
        log.info("       Map.of(\"price\", 999.99, \"quantity\", 1),");
        log.info("       Purchase.class");
        log.info("     ).subscribe(...)");
        log.info("");

        log.info("📌 insertRelation(table, data, resultType)");
        log.info("   Insert relation (table can be null to infer from data.id)");
        log.info("   Example:");
        log.info("     client.insertRelation(\"follows\", Map.of(");
        log.info("       \"in\", \"user:alice\",");
        log.info("       \"out\", \"user:bob\",");
        log.info("       \"since\", \"2024-01-01\"");
        log.info("     ), Relation.class).subscribe(...)");
        log.info("");
    }

    private static void demonstrateLiveMethods() {
        log.info("6️⃣ LIVE QUERIES (REAL-TIME)");
        log.info("─────────────────────────────────────────────────────────");
        log.info("");

        log.info("📌 live(table, diff)");
        log.info("   Start live query (returns UUID)");
        log.info("   Example:");
        log.info("     client.live(\"users\", false)");
        log.info("       .subscribe(liveId -> {");
        log.info("         log.info(\"Live query started: {}\", liveId);");
        log.info("         // Now listen to events...");
        log.info("       })");
        log.info("");

        log.info("📌 listenToLiveQuery(liveQueryId)");
        log.info("   Subscribe to live query notifications");
        log.info("   Example:");
        log.info("     client.listenToLiveQuery(liveId)");
        log.info("       .subscribe(event -> {");
        log.info("         String action = event.get(\"action\"); // CREATE, UPDATE, DELETE");
        log.info("         Object record = event.get(\"result\");");
        log.info("         log.info(\"Event: {} -> {}\", action, record);");
        log.info("       })");
        log.info("");

        log.info("📌 kill(liveQueryId)");
        log.info("   Stop a live query");
        log.info("   Example: client.kill(liveId).subscribe()");
        log.info("");

        log.info("💡 Complete Live Query Example:");
        log.info("   Mono<String> liveIdMono = client.live(\"orders\", false);");
        log.info("   liveIdMono.flatMapMany(liveId -> ");
        log.info("     client.listenToLiveQuery(liveId)");
        log.info("       .doOnNext(event -> processEvent(event))");
        log.info("       .doFinally(s -> client.kill(liveId).subscribe())");
        log.info("   ).subscribe();");
        log.info("");
    }
}
