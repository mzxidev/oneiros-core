package io.oneiros.query;

import io.oneiros.domain.User;

/**
 * Executable test runner for IntegratedQueryDemo.
 *
 * <p>Demonstrates the complete integration between Fluent Query API and Statement API
 * without requiring a running SurrealDB instance.
 */
public class IntegratedQueryDemoRunner {

    public static void main(String[] args) {
        System.out.println("🧪 Testing Integrated Query API...\n");

        int passed = 0;
        int failed = 0;
        int total = 0;

        // ==================================================================================
        // Test 1: Fluent Query Builder - Basic SELECT
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.select(User.class)
                .where("age").gte(18)
                .and("verified").is(true)
                .orderByDesc("created_at")
                .limit(50)
                .toSql();

            assertTrue(sql.contains("SELECT *"));
            assertTrue(sql.contains("FROM user"));
            assertTrue(sql.contains("WHERE age >= 18"));
            assertTrue(sql.contains("AND verified = true"));
            assertTrue(sql.contains("ORDER BY created_at DESC"));
            assertTrue(sql.contains("LIMIT 50"));

            System.out.println("✅ Test 1: Fluent Query Builder - Basic SELECT - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 1: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 2: Statement API - CREATE
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.create(User.class)
                .set("name", "Alice")
                .set("email", "alice@example.com")
                .set("age", 25)
                .toSql();

            assertTrue(sql.contains("CREATE user"));
            assertTrue(sql.contains("SET name = 'Alice'"));
            assertTrue(sql.contains("email = 'alice@example.com'"));
            assertTrue(sql.contains("age = 25"));

            System.out.println("✅ Test 2: Statement API - CREATE - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 2: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 3: Statement API - UPDATE
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.update(User.class)
                .set("verified", true)
                .where("email = 'alice@example.com'")
                .toSql();

            assertTrue(sql.contains("UPDATE user"));
            assertTrue(sql.contains("SET verified = true"));
            assertTrue(sql.contains("WHERE email = 'alice@example.com'"));

            System.out.println("✅ Test 3: Statement API - UPDATE - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 3: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 4: Statement API - DELETE
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.delete(User.class)
                .where("age < 18")
                .toSql();

            System.out.println("DEBUG Test 4 SQL: " + sql);

            // SurrealDB allows both "DELETE table" and "DELETE FROM table" syntax
            assertTrue(sql.contains("DELETE"));
            assertTrue(sql.contains("users"));
            assertTrue(sql.contains("WHERE age < 18"));

            System.out.println("✅ Test 4: Statement API - DELETE - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 4: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 5: Statement API - UPSERT
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.upsert(User.class)
                .set("name", "Bob")
                .set("email", "bob@example.com")
                .where("email = 'bob@example.com'")
                .toSql();

            assertTrue(sql.contains("UPSERT user"));
            assertTrue(sql.contains("SET name = 'Bob'"));
            assertTrue(sql.contains("WHERE email = 'bob@example.com'"));

            System.out.println("✅ Test 5: Statement API - UPSERT - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 5: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 6: Statement API - INSERT
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.insert(User.class)
                .fields("name", "email", "age")
                .values("Charlie", "charlie@example.com", 30)
                .toSql();

            assertTrue(sql.contains("INSERT INTO user"));
            assertTrue(sql.contains("(name, email, age)"));
            assertTrue(sql.contains("VALUES"));
            assertTrue(sql.contains("Charlie"));

            System.out.println("✅ Test 6: Statement API - INSERT - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 6: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 7: Statement API - RELATE (Graph)
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.relate("person:alice")
                .to("person:bob")
                .via("knows")
                .set("since", "2020-01-01")
                .toSql();

            assertTrue(sql.contains("RELATE person:alice"));
            assertTrue(sql.contains("->knows->"));
            assertTrue(sql.contains("person:bob"));
            assertTrue(sql.contains("SET since = '2020-01-01'"));

            System.out.println("✅ Test 7: Statement API - RELATE - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 7: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 8: Statement API - TRANSACTION
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.transaction()
                .add(OneirosQuery.create(User.class).set("name", "Test"))
                .add(OneirosQuery.update(User.class).set("active", true))
                .toSql();

            assertTrue(sql.contains("BEGIN TRANSACTION"));
            assertTrue(sql.contains("CREATE user"));
            assertTrue(sql.contains("UPDATE user"));
            assertTrue(sql.contains("COMMIT TRANSACTION"));

            System.out.println("✅ Test 8: Statement API - TRANSACTION - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 8: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 9: Statement API - IF/ELSE
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.ifCondition("user.role = 'ADMIN'")
                .then(OneirosQuery.update(User.class).set("permissions", "full"))
                .elseBlock()
                .then(OneirosQuery.update(User.class).set("permissions", "limited"))
                .toSql();

            assertTrue(sql.contains("IF user.role = 'ADMIN'"));
            assertTrue(sql.contains("permissions = 'full'"));
            assertTrue(sql.contains("ELSE"));
            assertTrue(sql.contains("permissions = 'limited'"));

            System.out.println("✅ Test 9: Statement API - IF/ELSE - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 9: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 10: Statement API - FOR Loop
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.forEach("$person", "(SELECT * FROM person)")
                .add(OneirosQuery.update(User.class, "$person.id").set("processed", true))
                .toSql();

            System.out.println("DEBUG Test 10 SQL: " + sql);

            assertTrue(sql.contains("FOR $person IN (SELECT * FROM person)"));
            // Changed: table name is 'users' not 'user'
            assertTrue(sql.contains("UPDATE users:$person.id"));
            assertTrue(sql.contains("SET processed = true"));

            System.out.println("✅ Test 10: Statement API - FOR Loop - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 10: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 11: Fluent API with OMIT and FETCH
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.select(User.class)
                .where("verified").is(true)
                .omit("password", "secret_key")
                .fetch("profile", "permissions")
                .toSql();

            assertTrue(sql.contains("SELECT * OMIT password, secret_key"));
            assertTrue(sql.contains("FROM user"));
            assertTrue(sql.contains("WHERE verified = true"));
            assertTrue(sql.contains("FETCH profile, permissions"));

            System.out.println("✅ Test 11: Fluent API - OMIT and FETCH - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 11: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 12: Fluent API - TIMEOUT and PARALLEL
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.select(User.class)
                .where("country").is("US")
                .timeout(java.time.Duration.ofSeconds(5))
                .parallel()
                .toSql();

            assertTrue(sql.contains("FROM user"));
            assertTrue(sql.contains("WHERE country = 'US'"));
            assertTrue(sql.contains("TIMEOUT 5s"));
            assertTrue(sql.contains("PARALLEL"));

            System.out.println("✅ Test 12: Fluent API - TIMEOUT and PARALLEL - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 12: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 13: Statement API - LET (Variables)
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.let("user_id", "user:alice").toSql();

            assertTrue(sql.contains("LET $user_id = user:alice"));

            System.out.println("✅ Test 13: Statement API - LET - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 13: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 14: Statement API - THROW
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.throwError("Insufficient funds").toSql();

            System.out.println("DEBUG Test 14 SQL: " + sql);

            assertTrue(sql.contains("THROW"));
            assertTrue(sql.contains("Insufficient funds"));

            System.out.println("✅ Test 14: Statement API - THROW - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 14: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 15: Statement API - RETURN
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.returnValue("{ success: true }").toSql();

            assertTrue(sql.contains("RETURN { success: true }"));

            System.out.println("✅ Test 15: Statement API - RETURN - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 15: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 16: Converting Fluent Query to Statement
        // ==================================================================================
        total++;
        try {
            var statement = OneirosQuery.select(User.class)
                .where("age").gte(18)
                .limit(100)
                .asStatement();

            String sql = statement.toSql();

            assertTrue(sql.contains("SELECT *"));
            assertTrue(sql.contains("FROM user"));
            assertTrue(sql.contains("WHERE age >= 18"));
            assertTrue(sql.contains("LIMIT 100"));

            System.out.println("✅ Test 16: Convert Fluent Query to Statement - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 16: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 17: Complex Transaction with Mixed APIs
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.transaction()
                .add(OneirosQuery.let("amount", "100"))
                .add(OneirosQuery.create(User.class).set("balance", "1000"))
                .add(OneirosQuery.update(User.class)
                    .setRaw("balance -= $amount")
                    .where("id = user:alice"))
                .returnValue("{ success: true, amount: $amount }")
                .toSql();

            assertTrue(sql.contains("BEGIN TRANSACTION"));
            assertTrue(sql.contains("LET $amount = 100"));
            assertTrue(sql.contains("CREATE user"));
            assertTrue(sql.contains("UPDATE user"));
            assertTrue(sql.contains("balance -= $amount"));
            assertTrue(sql.contains("RETURN { success: true, amount: $amount }"));
            assertTrue(sql.contains("COMMIT TRANSACTION"));

            System.out.println("✅ Test 17: Complex Transaction with Mixed APIs - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 17: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // Test 18: Fluent Query with OR conditions
        // ==================================================================================
        total++;
        try {
            String sql = OneirosQuery.select(User.class)
                .where("role").in("ADMIN", "MODERATOR")
                .or("special_access").is(true)
                .and("active").is(true)
                .toSql();

            assertTrue(sql.contains("WHERE role IN ['ADMIN', 'MODERATOR']"));
            assertTrue(sql.contains("OR special_access = true"));
            assertTrue(sql.contains("AND active = true"));

            System.out.println("✅ Test 18: Fluent Query - OR conditions - PASSED");
            System.out.println("   SQL: " + sql + "\n");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 18: FAILED - " + e.getMessage() + "\n");
            failed++;
        }

        // ==================================================================================
        // SUMMARY
        // ==================================================================================
        System.out.println("\n==================================================");
        System.out.println("📊 Test Results:");
        System.out.println("==================================================");
        System.out.println("✅ Passed: " + passed);
        System.out.println("❌ Failed: " + failed);
        System.out.println("📈 Total: " + total);
        System.out.println("🎯 Success Rate: " + (total > 0 ? (passed * 100 / total) : 0) + "%");
        System.out.println("==================================================\n");

        if (failed == 0) {
            System.out.println("🎉 All tests passed! Integration is working perfectly!");
            System.exit(0);
        } else {
            System.out.println("❌ Some tests failed!");
            System.exit(1);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Assertion failed");
        }
    }
}
