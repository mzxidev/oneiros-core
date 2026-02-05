package io.oneiros.statement;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.statement.statements.SelectStatement;

/**
 * Test Runner für das neue Statement & Clause System
 */
public class StatementSystemTest {

    public static void main(String[] args) {
        System.out.println("🧪 Testing Statement & Clause System...\n");

        int passed = 0;
        int failed = 0;

        // Test 1: SELECT with all clauses
        try {
            var sql = SelectStatement.from(TestEntity.class)
                    .where("role = 'ADMIN'")
                    .and("status != 'DELETED'")
                    .omit("password")
                    .fetch("profile")
                    .groupBy("department")
                    .orderBy("name")
                    .limit(10, 20)
                    .parallel()
                    .toSql();

            assertTrue(contains(sql, "SELECT *"));
            assertTrue(contains(sql, "OMIT password"));
            assertTrue(contains(sql, "FROM test_entity"));
            assertTrue(contains(sql, "WHERE role = 'ADMIN'"));
            assertTrue(contains(sql, "AND status != 'DELETED'"));
            assertTrue(contains(sql, "GROUP BY department"));
            assertTrue(contains(sql, "ORDER BY name ASC"));
            assertTrue(contains(sql, "LIMIT 10 START 20"));
            assertTrue(contains(sql, "FETCH profile"));
            assertTrue(contains(sql, "PARALLEL"));

            System.out.println("✅ Test 1: SELECT with all clauses - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 1: SELECT with all clauses - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 2: SELECT with EXPLAIN
        try {
            var sql = SelectStatement.from(TestEntity.class)
                    .where("age > 18")
                    .explain()
                    .toSql();

            assertTrue(sql.startsWith("EXPLAIN SELECT"));
            assertTrue(contains(sql, "WHERE age > 18"));

            System.out.println("✅ Test 2: SELECT with EXPLAIN - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 2: SELECT with EXPLAIN - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 3: SELECT with SPLIT
        try {
            var sql = SelectStatement.from(TestEntity.class)
                    .split("category")
                    .toSql();

            assertTrue(contains(sql, "SPLIT category"));

            System.out.println("✅ Test 3: SELECT with SPLIT - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 3: SELECT with SPLIT - FAILED: " + e.getMessage());
            failed++;
        }


        // Test 7: SELECT with custom projection
        try {
            var sql = SelectStatement.from(TestEntity.class)
                    .select("name", "age", "email")
                    .where("active = true")
                    .toSql();

            assertTrue(contains(sql, "SELECT name, age, email"));
            assertTrue(contains(sql, "FROM test_entity"));

            System.out.println("✅ Test 7: SELECT with custom projection - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 7: SELECT with custom projection - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 8: Multiple ORDER BY
        try {
            var sql = SelectStatement.from(TestEntity.class)
                    .orderBy("name")
                    .orderByDesc("age")
                    .toSql();

            assertTrue(contains(sql, "ORDER BY name ASC"));
            assertTrue(contains(sql, "ORDER BY age DESC"));

            System.out.println("✅ Test 8: Multiple ORDER BY - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 8: Multiple ORDER BY - FAILED: " + e.getMessage());
            failed++;
        }

        // Zusammenfassung
        System.out.println("\n" + "=".repeat(50));
        System.out.println("📊 Test Results:");
        System.out.println("=".repeat(50));
        System.out.println("✅ Passed: " + passed);
        System.out.println("❌ Failed: " + failed);
        System.out.println("📈 Total: " + (passed + failed));
        System.out.println("🎯 Success Rate: " + (passed * 100 / (passed + failed)) + "%");
        System.out.println("=".repeat(50));

        if (failed == 0) {
            System.out.println("\n🎉 All tests passed!");
            System.exit(0);
        } else {
            System.out.println("\n❌ Some tests failed!");
            System.exit(1);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Condition was false");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean contains(String haystack, String needle) {
        return haystack.contains(needle);
    }

    @OneirosEntity("test_entity")
    static class TestEntity {
        String id;
        String name;
        int age;
    }
}
