package io.oneiros;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.query.OneirosQuery;

/**
 * Einfacher Test-Runner ohne JUnit - kann direkt ausgeführt werden
 */
public class QueryBuilderTestRunner {

    public static void main(String[] args) {
        System.out.println("🧪 Starte QueryBuilder Tests...\n");

        int passed = 0;
        int failed = 0;

        // Test 1: Simple WHERE
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("name").is("Alice")
                    .toSql();
            assertEquals("SELECT * FROM test_entity WHERE name = 'Alice'", sql);
            System.out.println("✅ Test 1: Simple WHERE - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 1: Simple WHERE - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 2: Multiple WHERE with AND
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("name").is("Alice")
                    .and("age").gt(18)
                    .toSql();
            assertEquals("SELECT * FROM test_entity WHERE name = 'Alice' AND age > 18", sql);
            System.out.println("✅ Test 2: Multiple WHERE with AND - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 2: Multiple WHERE with AND - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 3: OR operator
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("role").is("ADMIN")
                    .or("role").is("MODERATOR")
                    .toSql();
            assertEquals("SELECT * FROM test_entity WHERE role = 'ADMIN' OR role = 'MODERATOR'", sql);
            System.out.println("✅ Test 3: OR operator - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 3: OR operator - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 4: IN operator
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("status").in("ACTIVE", "PENDING")
                    .toSql();
            assertEquals("SELECT * FROM test_entity WHERE status IN ['ACTIVE', 'PENDING']", sql);
            System.out.println("✅ Test 4: IN operator - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 4: IN operator - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 5: LIKE operator
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("email").like("@gmail.com")
                    .toSql();
            assertEquals("SELECT * FROM test_entity WHERE email CONTAINS '@gmail.com'", sql);
            System.out.println("✅ Test 5: LIKE operator - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 5: LIKE operator - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 6: NOT EQUALS
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("status").notEquals("DELETED")
                    .toSql();
            assertEquals("SELECT * FROM test_entity WHERE status != 'DELETED'", sql);
            System.out.println("✅ Test 6: NOT EQUALS operator - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 6: NOT EQUALS operator - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 7: Greater/Less Than
        try {
            var sql1 = OneirosQuery.select(TestEntity.class).where("age").gt(18).toSql();
            assertEquals("SELECT * FROM test_entity WHERE age > 18", sql1);

            var sql2 = OneirosQuery.select(TestEntity.class).where("age").lt(65).toSql();
            assertEquals("SELECT * FROM test_entity WHERE age < 65", sql2);

            var sql3 = OneirosQuery.select(TestEntity.class).where("age").gte(18).toSql();
            assertEquals("SELECT * FROM test_entity WHERE age >= 18", sql3);

            var sql4 = OneirosQuery.select(TestEntity.class).where("age").lte(65).toSql();
            assertEquals("SELECT * FROM test_entity WHERE age <= 65", sql4);

            System.out.println("✅ Test 7: Comparison operators (>, <, >=, <=) - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 7: Comparison operators - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 8: BETWEEN
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("age").between(18, 65)
                    .toSql();
            assertEquals("SELECT * FROM test_entity WHERE age >= 18 AND age <= 65", sql);
            System.out.println("✅ Test 8: BETWEEN operator - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 8: BETWEEN operator - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 9: IS NULL / IS NOT NULL
        try {
            var sql1 = OneirosQuery.select(TestEntity.class).where("deletedAt").isNull().toSql();
            assertEquals("SELECT * FROM test_entity WHERE deletedAt IS NULL", sql1);

            var sql2 = OneirosQuery.select(TestEntity.class).where("email").isNotNull().toSql();
            assertEquals("SELECT * FROM test_entity WHERE email IS NOT NULL", sql2);

            System.out.println("✅ Test 9: NULL checks (IS NULL, IS NOT NULL) - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 9: NULL checks - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 10: ORDER BY
        try {
            var sql1 = OneirosQuery.select(TestEntity.class).orderBy("name").toSql();
            assertEquals("SELECT * FROM test_entity ORDER BY name ASC", sql1);

            var sql2 = OneirosQuery.select(TestEntity.class).orderByDesc("createdAt").toSql();
            assertEquals("SELECT * FROM test_entity ORDER BY createdAt DESC", sql2);

            System.out.println("✅ Test 10: ORDER BY (ASC/DESC) - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 10: ORDER BY - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 11: LIMIT
        try {
            var sql = OneirosQuery.select(TestEntity.class).limit(10).toSql();
            assertEquals("SELECT * FROM test_entity LIMIT 10", sql);
            System.out.println("✅ Test 11: LIMIT - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 11: LIMIT - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 12: OFFSET
        try {
            var sql = OneirosQuery.select(TestEntity.class).offset(20).limit(10).toSql();
            assertEquals("SELECT * FROM test_entity LIMIT 10 START 20", sql);
            System.out.println("✅ Test 12: OFFSET (START) - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 12: OFFSET - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 13: Complex Query
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("role").in("ADMIN", "MODERATOR")
                    .and("status").notEquals("DELETED")
                    .orderByDesc("createdAt")
                    .limit(20)
                    .toSql();

            assertTrue(sql.contains("SELECT * FROM test_entity WHERE"));
            assertTrue(sql.contains("role IN ['ADMIN', 'MODERATOR']"));
            assertTrue(sql.contains("status != 'DELETED'"));
            assertTrue(sql.contains("ORDER BY createdAt DESC"));
            assertTrue(sql.contains("LIMIT 20"));

            System.out.println("✅ Test 13: Complex Query - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 13: Complex Query - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 14: String Escaping
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("name").is("O'Brien")
                    .toSql();
            assertEquals("SELECT * FROM test_entity WHERE name = 'O\\'Brien'", sql);
            System.out.println("✅ Test 14: String Escaping - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 14: String Escaping - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 15: Number Values
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("age").is(25)
                    .toSql();
            assertTrue(sql.contains("age = 25"));
            assertTrue(!sql.contains("'25'"));
            System.out.println("✅ Test 15: Number Values (without quotes) - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 15: Number Values - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 16: Boolean Values
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("active").is(true)
                    .toSql();
            assertEquals("SELECT * FROM test_entity WHERE active = true", sql);
            System.out.println("✅ Test 16: Boolean Values - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 16: Boolean Values - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 17: Empty Query
        try {
            var sql = OneirosQuery.select(TestEntity.class).toSql();
            assertEquals("SELECT * FROM test_entity", sql);
            System.out.println("✅ Test 17: Empty Query (SELECT all) - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 17: Empty Query - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 18: OMIT single field
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .omit("password")
                    .toSql();
            assertEquals("SELECT * OMIT password FROM test_entity", sql);
            System.out.println("✅ Test 18: OMIT single field - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 18: OMIT single field - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 19: OMIT multiple fields
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .omit("password", "secretKey")
                    .toSql();
            assertEquals("SELECT * OMIT password, secretKey FROM test_entity", sql);
            System.out.println("✅ Test 19: OMIT multiple fields - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 19: OMIT multiple fields - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 20: OMIT with WHERE
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .omit("password")
                    .where("active").is(true)
                    .toSql();
            assertEquals("SELECT * OMIT password FROM test_entity WHERE active = true", sql);
            System.out.println("✅ Test 20: OMIT with WHERE - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 20: OMIT with WHERE - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 21: OMIT with nested fields (opts.security)
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .omit("password", "opts.security")
                    .toSql();
            assertEquals("SELECT * OMIT password, opts.security FROM test_entity", sql);
            System.out.println("✅ Test 21: OMIT nested fields - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 21: OMIT nested fields - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 22: OMIT with ORDER BY and LIMIT
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .omit("password")
                    .orderByDesc("createdAt")
                    .limit(10)
                    .toSql();
            assertEquals("SELECT * OMIT password FROM test_entity ORDER BY createdAt DESC LIMIT 10", sql);
            System.out.println("✅ Test 22: OMIT with ORDER BY and LIMIT - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 22: OMIT with ORDER BY and LIMIT - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 23: FETCH single field
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .fetch("profile")
                    .toSql();
            assertEquals("SELECT * FROM test_entity FETCH profile", sql);
            System.out.println("✅ Test 23: FETCH single field - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 23: FETCH single field - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 24: FETCH multiple fields
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .fetch("profile", "permissions", "posts")
                    .toSql();
            assertEquals("SELECT * FROM test_entity FETCH profile, permissions, posts", sql);
            System.out.println("✅ Test 24: FETCH multiple fields - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 24: FETCH multiple fields - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 25: FETCH with WHERE
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("active").is(true)
                    .fetch("profile")
                    .toSql();
            assertEquals("SELECT * FROM test_entity WHERE active = true FETCH profile", sql);
            System.out.println("✅ Test 25: FETCH with WHERE - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 25: FETCH with WHERE - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 26: TIMEOUT
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .timeout(java.time.Duration.ofSeconds(3))
                    .toSql();
            assertEquals("SELECT * FROM test_entity TIMEOUT 3s", sql);
            System.out.println("✅ Test 26: TIMEOUT - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 26: TIMEOUT - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 27: TIMEOUT with milliseconds
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .timeout(java.time.Duration.ofMillis(1500))
                    .toSql();
            assertEquals("SELECT * FROM test_entity TIMEOUT 1s500ms", sql);
            System.out.println("✅ Test 27: TIMEOUT with milliseconds - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 27: TIMEOUT with milliseconds - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 28: PARALLEL
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .parallel()
                    .toSql();
            assertEquals("SELECT * FROM test_entity PARALLEL", sql);
            System.out.println("✅ Test 28: PARALLEL - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 28: PARALLEL - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 29: Complete query with all features
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("role").is("ADMIN")
                    .omit("password", "history")
                    .fetch("profile", "permissions")
                    .timeout(java.time.Duration.ofSeconds(3))
                    .parallel()
                    .toSql();

            assertTrue(sql.contains("WHERE role = 'ADMIN'"));
            assertTrue(sql.contains("OMIT password, history"));
            assertTrue(sql.contains("FETCH profile, permissions"));
            assertTrue(sql.contains("TIMEOUT 3s"));
            assertTrue(sql.contains("PARALLEL"));

            System.out.println("✅ Test 29: Complete query with all features - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 29: Complete query with all features - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 30: FETCH with ORDER BY and LIMIT
        try {
            var sql = OneirosQuery.select(TestEntity.class)
                    .where("active").is(true)
                    .orderByDesc("createdAt")
                    .limit(10)
                    .fetch("posts")
                    .toSql();
            assertEquals("SELECT * FROM test_entity WHERE active = true ORDER BY createdAt DESC LIMIT 10 FETCH posts", sql);
            System.out.println("✅ Test 30: FETCH with ORDER BY and LIMIT - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 30: FETCH with ORDER BY and LIMIT - FAILED: " + e.getMessage());
            failed++;
        }


        // Zusammenfassung
        System.out.println("\n" + "=".repeat(50));
        System.out.println("📊 Test-Ergebnis:");
        System.out.println("=".repeat(50));
        System.out.println("✅ Erfolgreich: " + passed);
        System.out.println("❌ Fehlgeschlagen: " + failed);
        System.out.println("📈 Gesamt: " + (passed + failed));
        System.out.println("🎯 Erfolgsquote: " + (passed * 100 / (passed + failed)) + "%");
        System.out.println("=".repeat(50));

        if (failed == 0) {
            System.out.println("\n🎉 Alle Tests erfolgreich!");
            System.exit(0);
        } else {
            System.out.println("\n❌ Einige Tests sind fehlgeschlagen!");
            System.exit(1);
        }
    }

    // Simple Assertions
    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + "\nActual: " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Condition was false");
        }
    }

    // Test Entity
    @OneirosEntity("test_entity")
    static class TestEntity {
        String id;
        String name;
        int age;
    }
}
