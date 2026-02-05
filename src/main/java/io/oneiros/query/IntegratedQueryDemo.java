package io.oneiros.query;

import io.oneiros.client.OneirosClient;
import io.oneiros.domain.User;

/**
 * Comprehensive examples showing the integration of Query Builder and Statement API.
 *
 * <p>This demonstrates how OneirosQuery now provides two seamless approaches:
 * <ul>
 *   <li><b>Fluent Query API</b> - for simple, common queries</li>
 *   <li><b>Statement API</b> - for complex operations and full SurrealQL access</li>
 * </ul>
 */
public class IntegratedQueryDemo {

    private final OneirosClient client;

    public IntegratedQueryDemo(OneirosClient client) {
        this.client = client;
    }

    // ==================================================================================
    // EXAMPLE 1: Using Fluent API (Original OneirosQuery)
    // ==================================================================================

    public void fluentApiExamples() {
        // Simple SELECT with fluent API
        OneirosQuery.select(User.class)
            .where("age").gte(18)
            .and("verified").is(true)
            .orderByDesc("created_at")
            .limit(50)
            .omit("password", "secret_key")
            .fetch("profile", "permissions")
            .execute(client)
            .subscribe(user -> System.out.println("Found: " + user));

        // Complex conditions with OR
        OneirosQuery.select(User.class)
            .where("role").in("ADMIN", "MODERATOR")
            .or("special_access").is(true)
            .and("active").is(true)
            .execute(client);

        // With timeout and parallel execution
        OneirosQuery.select(User.class)
            .where("country").is("US")
            .timeout(java.time.Duration.ofSeconds(5))
            .parallel()
            .execute(client);
    }

    // ==================================================================================
    // EXAMPLE 2: Using Statement API (New Integration)
    // ==================================================================================

    public void statementApiExamples() {
        // CREATE
        OneirosQuery.create(User.class)
            .set("name", "Alice")
            .set("email", "alice@example.com")
            .set("age", 25)
            .returnAfter()
            .execute(client)
            .subscribe(user -> System.out.println("Created: " + user));

        // UPDATE
        OneirosQuery.update(User.class)
            .set("verified", true)
            .set("verified_at", "time::now()")
            .where("email = 'alice@example.com'")
            .returnAfter()
            .execute(client);

        // DELETE
        OneirosQuery.delete(User.class)
            .where("age < 18")
            .returnBefore()
            .execute(client);

        // UPSERT (insert or update)
        OneirosQuery.upsert(User.class)
            .set("name", "Bob")
            .set("email", "bob@example.com")
            .where("email = 'bob@example.com'")
            .execute(client);

        // INSERT with duplicate key handling
        OneirosQuery.insert(User.class)
            .fields("name", "email", "age")
            .values("Charlie", "charlie@example.com", 30)
            .onDuplicateKeyUpdate()
                .set("updated_at", "time::now()")
                .set("login_count", "login_count + 1")
            .end()
            .execute(client);
    }

    // ==================================================================================
    // EXAMPLE 3: Graph Relationships
    // ==================================================================================

    public void graphExamples() {
        // Create a friendship relation
        OneirosQuery.relate("person:alice")
            .to("person:bob")
            .via("knows")
            .set("since", "2020-01-01")
            .set("strength", 8)
            .execute(client);

        // Query with graph traversal
        OneirosQuery.select(User.class)
            .where("->knows->person->name = 'Bob'")
            .fetch("->knows->person.profile")
            .execute(client);

        // Multiple relationships at once
        OneirosQuery.relate("[person:alice, person:charlie]")
            .to("company:surrealdb")
            .via("works_at")
            .set("position", "Engineer")
            .set("start_date", "2023-01-01")
            .execute(client);
    }

    // ==================================================================================
    // EXAMPLE 4: Transactions
    // ==================================================================================

    public void transactionExamples() {
        // Simple transaction
        OneirosQuery.transaction()
            .add(OneirosQuery.create(User.class)
                .set("name", "Test User")
                .set("email", "test@example.com"))
            .add(OneirosQuery.update(User.class)
                .set("verified", true)
                .where("email = 'test@example.com'"))
            .commit(client);

        // Transaction with error handling
        OneirosQuery.transaction()
            .add(OneirosQuery.let("amount", "100"))
            .add(OneirosQuery.let("from", "account:alice"))
            .add(OneirosQuery.let("to", "account:bob"))

            // Check balance
            .add(OneirosQuery.ifCondition("$from.balance < $amount")
                .then(OneirosQuery.throwError("Insufficient funds")))

            // Transfer money
            .add(OneirosQuery.update(User.class)
                .setRaw("balance -= $amount")
                .where("id = $from"))
            .add(OneirosQuery.update(User.class)
                .setRaw("balance += $amount")
                .where("id = $to"))

            .returnValue("{ success: true, amount: $amount }")
            .commit(client);
    }

    // ==================================================================================
    // EXAMPLE 5: Conditional Logic
    // ==================================================================================

    public void conditionalExamples() {
        // Simple IF/ELSE
        OneirosQuery.ifCondition("user.role = 'ADMIN'")
            .then(OneirosQuery.update(User.class)
                .set("permissions", "full")
                .where("id = user.id"))
            .elseBlock()
            .then(OneirosQuery.update(User.class)
                .set("permissions", "limited")
                .where("id = user.id"))
            .build()
            .execute(client);

        // Nested conditions
        OneirosQuery.ifCondition("user.age >= 18")
            .then(OneirosQuery.ifCondition("user.verified = true")
                .then(OneirosQuery.update(User.class)
                    .set("can_vote", true))
                .elseBlock()
                .then(OneirosQuery.throwError("User not verified"))
                .build())
            .elseBlock()
            .then(OneirosQuery.throwError("User must be 18 or older"))
            .build()
            .execute(client);
    }

    // ==================================================================================
    // EXAMPLE 6: FOR Loops
    // ==================================================================================

    public void loopExamples() {
        // Update all adult users
        OneirosQuery.forEach("$person", "(SELECT * FROM person WHERE age >= 18)")
            .add(OneirosQuery.update(User.class, "$person.id")
                .set("can_vote", true))
            .execute(client);

        // Loop with CONTINUE
        OneirosQuery.forEach("$user", "(SELECT * FROM user)")
            .add(OneirosQuery.ifCondition("$user.age < 18")
                .then(OneirosQuery.continueLoop()))
            .add(OneirosQuery.update(User.class, "$user.id")
                .set("adult", true))
            .execute(client);

        // Loop over range
        OneirosQuery.forEach("$i", "0..100")
            .add(OneirosQuery.create(User.class)
                .set("name", "'User ' + $i")
                .set("index", "$i"))
            .execute(client);
    }

    // ==================================================================================
    // EXAMPLE 7: Combining Both APIs
    // ==================================================================================

    public void combinedExamples() {
        // Use fluent query builder, then convert to statement for transaction
        var userQuery = OneirosQuery.select(User.class)
            .where("age").gte(18)
            .and("verified").is(true)
            .limit(100)
            .asStatement();

        OneirosQuery.transaction()
            .add(userQuery)
            .add(OneirosQuery.update(User.class)
                .set("processed", true)
                .where("age >= 18 AND verified = true"))
            .commit(client);

        // Fluent query with statement API operations
        OneirosQuery.select(User.class)
            .where("active").is(true)
            .execute(client)
            .flatMap(user ->
                // For each result, execute an update
                OneirosQuery.update(User.class, user.getId())
                    .set("last_accessed", "time::now()")
                    .executeOne(client)
            )
            .subscribe();

        // Complex transaction with multiple approaches
        OneirosQuery.transaction()
            // Use statement API
            .add(OneirosQuery.create(User.class)
                .set("name", "Combined Example")
                .set("email", "combined@example.com"))

            // Use raw SurrealQL
            .addRaw("LET $user_id = (SELECT id FROM user WHERE email = 'combined@example.com').id")

            // Use fluent query converted to statement
            .add(OneirosQuery.select(User.class)
                .where("id").is("$user_id")
                .asStatement())

            .returnValue("$user_id")
            .commit(client);
    }

    // ==================================================================================
    // EXAMPLE 8: Real-World Use Cases
    // ==================================================================================

    public void realWorldExamples() {
        // User registration with validation
        OneirosQuery.transaction()
            .add(OneirosQuery.let("email", "'newuser@example.com'"))
            .add(OneirosQuery.let("existing", "(SELECT * FROM user WHERE email = $email)"))

            .add(OneirosQuery.ifCondition("count($existing) > 0")
                .then(OneirosQuery.throwError("Email already exists")))

            .add(OneirosQuery.create(User.class)
                .set("email", "$email")
                .set("name", "'New User'")
                .set("created_at", "time::now()")
                .set("verified", false))

            .returnValue("{ success: true, message: 'User created' }")
            .commit(client);

        // Bulk update with conditions
        OneirosQuery.forEach("$user", "(SELECT * FROM user WHERE last_login < time::now() - 30d)")
            .add(OneirosQuery.ifCondition("$user.premium = true")
                .then(OneirosQuery.update(User.class, "$user.id")
                    .set("status", "'inactive_premium'"))
                .elseBlock()
                .then(OneirosQuery.delete(User.class, "$user.id"))
                .build())
            .execute(client);

        // Social network friend suggestions
        OneirosQuery.select(User.class)
            .where("->knows->person->(knows WHERE influencer = true)")
            .and("id != 'user:current'")
            .omit("password", "email")
            .fetch("->knows->person.profile")
            .limit(10)
            .execute(client);

        // Analytics query
        OneirosQuery.select(User.class)
            .where("created_at >= time::now() - 7d")
            .and("verified").is(true)
            .timeout(java.time.Duration.ofSeconds(10))
            .parallel()
            .execute(client)
            .count()
            .subscribe(count -> System.out.println("New verified users this week: " + count));
    }

    // ==================================================================================
    // EXAMPLE 9: Error Handling Patterns
    // ==================================================================================

    public void errorHandlingExamples() {
        // Transaction with rollback on error
        OneirosQuery.transaction()
            .add(OneirosQuery.create(User.class)
                .set("name", "Test"))

            .add(OneirosQuery.ifCondition("$some_validation_fails")
                .then(OneirosQuery.throwError("Validation failed")))

            .add(OneirosQuery.update(User.class)
                .set("status", "'active'"))

            .commit(client)
            .onErrorResume(error -> {
                System.err.println("Transaction failed: " + error.getMessage());
                return OneirosQuery.transaction()
                    .addRaw("CANCEL TRANSACTION")
                    .rollback(client);
            })
            .subscribe();

        // Retry pattern
        OneirosQuery.create(User.class)
            .set("name", "Retry Example")
            .execute(client)
            .retry(3)
            .onErrorResume(error -> {
                System.err.println("Failed after retries: " + error.getMessage());
                return OneirosQuery.upsert(User.class)
                    .set("name", "Retry Example")
                    .execute(client);
            })
            .subscribe();
    }

    // ==================================================================================
    // EXAMPLE 10: Performance Optimization
    // ==================================================================================

    public void performanceExamples() {
        // Parallel execution for large datasets
        OneirosQuery.select(User.class)
            .where("country").in("US", "UK", "DE", "FR")
            .parallel()
            .execute(client)
            .buffer(100)
            .flatMap(batch -> processBatch(batch))
            .subscribe();

        // Pagination with offset
        for (int page = 0; page < 10; page++) {
            final int currentPage = page;
            OneirosQuery.select(User.class)
                .where("active").is(true)
                .orderBy("created_at")
                .limit(100)
                .offset(currentPage * 100)
                .execute(client)
                .subscribe(users -> System.out.println("Page " + currentPage + ": " + users));
        }

        // UPSERT with unique index optimization
        OneirosQuery.upsert(User.class)
            .set("name", "Bob")
            .set("email", "bob@example.com")
            .where("email = 'bob@example.com'")
            .execute(client)
            .subscribe(user -> System.out.println("Upserted efficiently: " + user));
    }

    // Helper method for batch processing
    private reactor.core.publisher.Mono<Void> processBatch(java.util.List<User> batch) {
        System.out.println("Processing batch of " + batch.size() + " users");
        return reactor.core.publisher.Mono.empty();
    }
}
