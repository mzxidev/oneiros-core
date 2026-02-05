package io.oneiros.transaction;

/**
 * Tests für die Fluent API des TransactionBuilders.
 */
public class FluentTransactionTest {

    public static void main(String[] args) {
        System.out.println("🧪 Testing Fluent Transaction API...\n");

        int passed = 0;
        int failed = 0;

        // Test 1: CREATE with fluent API
        try {
            var sql = TransactionBuilder.begin()
                    .create("account:one").set("balance", 1000).end()
                    .create("account:two").set("balance", 500).end()
                    .toSql();

            assertTrue(contains(sql, "CREATE account:one SET balance = 1000"));
            assertTrue(contains(sql, "CREATE account:two SET balance = 500"));

            System.out.println("✅ Test 1: CREATE with fluent API - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 1: CREATE with fluent API - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 2: UPDATE with fluent API
        try {
            var sql = TransactionBuilder.begin()
                    .update("account:one").set("balance -= 100").end()
                    .update("account:two").set("balance += 100").end()
                    .toSql();

            assertTrue(sql.contains("UPDATE account:one SET balance -= 100"));
            assertTrue(sql.contains("UPDATE account:two SET balance += 100"));

            System.out.println("✅ Test 2: UPDATE with fluent API - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 2: UPDATE with fluent API - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 3: IF condition with fluent API
        try {
            var sql = TransactionBuilder.begin()
                    .create("account:two").set("balance", 50).end()
                    .ifCondition()
                        .field("account:two.balance").lt(100)
                        .then().throwError("Insufficient funds!").endIf()
                    .toSql();

            assertTrue(sql.contains("IF account:two.balance < 100"));
            assertTrue(sql.contains("THROW \"Insufficient funds!\""));

            System.out.println("✅ Test 3: IF condition with fluent API - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 3: IF condition with fluent API - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 4: SELECT with clauses
        try {
            var sql = TransactionBuilder.begin()
                    .select("name", "balance")
                        .from("account")
                        .where("balance > 100")
                        .omit("password")
                        .fetch("profile")
                        .limit(10)
                        .end()
                    .toSql();

            assertTrue(sql.contains("SELECT name, balance"));
            assertTrue(sql.contains("OMIT password"));
            assertTrue(sql.contains("FROM account"));
            assertTrue(sql.contains("WHERE balance > 100"));
            assertTrue(sql.contains("FETCH profile"));
            assertTrue(sql.contains("LIMIT 10"));

            System.out.println("✅ Test 4: SELECT with clauses - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 4: SELECT with clauses - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 5: Complete bank transfer transaction
        try {
            var sql = TransactionBuilder.begin()
                    .let("amount", "100")
                    .ifCondition()
                        .field("account:one.balance").gte(100)
                        .then()
                            .update("account:one").set("balance -= $amount").endUpdate()
                            .update("account:two").set("balance += $amount").endUpdate()
                        .endIf()
                    .returnValue("'Transfer successful'")
                    .toSql();

            assertTrue(contains(sql, "LET $amount = 100"));
            assertTrue(contains(sql, "IF account:one.balance >= 100"));
            assertTrue(contains(sql, "UPDATE account:one"));
            assertTrue(contains(sql, "UPDATE account:two"));
            assertTrue(contains(sql, "RETURN 'Transfer successful'"));

            System.out.println("✅ Test 5: Complete bank transfer - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 5: Complete bank transfer - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 6: IF with AND condition
        try {
            var sql = TransactionBuilder.begin()
                    .ifCondition()
                        .field("user.role").eq("ADMIN")
                        .and()
                        .field("user.active").isTrue()
                        .then().throwError("Access denied!").endIf()
                    .toSql();

            assertTrue(sql.contains("IF user.role = 'ADMIN' AND user.active = true"));
            assertTrue(sql.contains("THROW \"Access denied!\""));

            System.out.println("✅ Test 6: IF with AND condition - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 6: IF with AND condition - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 7: Chain multiple statements
        try {
            var sql = TransactionBuilder.begin()
                    .create("user:alice").set("name", "Alice").end()
                    .create("user:bob").set("name", "Bob").end()
                    .update("user:alice").set("friend_count += 1").end()
                    .update("user:bob").set("friend_count += 1").end()
                    .toSql();

            assertTrue(sql.contains("CREATE user:alice SET name = 'Alice'"));
            assertTrue(sql.contains("CREATE user:bob SET name = 'Bob'"));
            assertTrue(sql.contains("UPDATE user:alice"));
            assertTrue(sql.contains("UPDATE user:bob"));

            System.out.println("✅ Test 7: Chain multiple statements - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 7: Chain multiple statements - FAILED: " + e.getMessage());
            failed++;
        }

        // Test 8: IF with NOT condition
        try {
            var sql = TransactionBuilder.begin()
                    .ifCondition()
                        .not()
                        .field("user.verified").isTrue()
                        .then().throwError("User not verified!").endIf()
                    .toSql();

            assertTrue(contains(sql, "IF !user.verified = true"));
            assertTrue(contains(sql, "THROW \"User not verified!\""));

            System.out.println("✅ Test 8: IF with NOT condition - PASSED");
            passed++;
        } catch (AssertionError e) {
            System.out.println("❌ Test 8: IF with NOT condition - FAILED: " + e.getMessage());
            failed++;
        }

        // Summary
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

    private static boolean contains(String haystack, String needle) {
        return haystack.contains(needle);
    }
}
