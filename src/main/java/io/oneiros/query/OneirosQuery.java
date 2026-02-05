package io.oneiros.query;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import io.oneiros.statement.statements.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Advanced Query Builder with integrated Statement API.
 *
 * <p>Provides two ways to build queries:
 * <ul>
 *   <li><b>Fluent Query API</b> - Simple, chainable methods for common queries</li>
 *   <li><b>Statement API</b> - Full SurrealQL statement access via static methods</li>
 * </ul>
 *
 * <h3>Fluent API Examples:</h3>
 * <pre>
 * // Simple SELECT
 * OneirosQuery.select(User.class)
 *     .where("age").gte(18)
 *     .orderBy("name")
 *     .limit(10)
 *     .execute(client);
 *
 * // With FETCH and OMIT
 * OneirosQuery.select(User.class)
 *     .where("verified").is(true)
 *     .omit("password")
 *     .fetch("profile", "permissions")
 *     .execute(client);
 * </pre>
 *
 * <h3>Statement API Examples:</h3>
 * <pre>
 * // CREATE
 * OneirosQuery.create(User.class)
 *     .set("name", "Alice")
 *     .set("email", "alice@example.com")
 *     .execute(client);
 *
 * // UPDATE
 * OneirosQuery.update(User.class)
 *     .set("verified", true)
 *     .where("id = user:alice")
 *     .execute(client);
 *
 * // TRANSACTION
 * OneirosQuery.transaction()
 *     .add(OneirosQuery.create(User.class).set("name", "Bob"))
 *     .add(OneirosQuery.update(Account.class).setRaw("balance += 100"))
 *     .commit(client);
 * </pre>
 */
public class OneirosQuery<T> {

    private final Class<T> type;
    private final String tableName;

    // Wir bauen die Query-Teile separat auf
    private final List<String> whereClauses = new ArrayList<>();
    private final List<String> omitFields = new ArrayList<>();
    private final List<String> fetchFields = new ArrayList<>();
    private String limitClause = "";
    private String offsetClause = "";
    private String orderByClause = "";
    private String timeoutClause = "";
    private boolean parallelEnabled = false;

    private String currentField;

    private OneirosQuery(Class<T> type) {
        this.type = type;
        this.tableName = getTableName(type);
    }

    // --- Entry Point ---
    public static <T> OneirosQuery<T> select(Class<T> type) {
        return new OneirosQuery<>(type);
    }

    // --- Filtering ---

    public OneirosQuery<T> where(String field) {
        this.currentField = field;
        return this;
    }

    public OneirosQuery<T> is(Object value) {
        addCondition(" = " + formatValue(value));
        return this;
    }

    public OneirosQuery<T> gt(Object value) {
        addCondition(" > " + formatValue(value));
        return this;
    }

    public OneirosQuery<T> lt(Object value) {
        addCondition(" < " + formatValue(value));
        return this;
    }

    public OneirosQuery<T> gte(Object value) {
        addCondition(" >= " + formatValue(value));
        return this;
    }

    public OneirosQuery<T> lte(Object value) {
        addCondition(" <= " + formatValue(value));
        return this;
    }

    public OneirosQuery<T> notEquals(Object value) {
        addCondition(" != " + formatValue(value));
        return this;
    }

    public OneirosQuery<T> like(String pattern) {
        addCondition(" CONTAINS " + formatValue(pattern));
        return this;
    }

    public OneirosQuery<T> in(Object... values) {
        StringBuilder inClause = new StringBuilder(" IN [");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) inClause.append(", ");
            inClause.append(formatValue(values[i]));
        }
        inClause.append("]");
        addCondition(inClause.toString());
        return this;
    }

    public OneirosQuery<T> between(Object min, Object max) {
        addCondition(" >= " + formatValue(min) + " AND " + currentField + " <= " + formatValue(max));
        return this;
    }

    public OneirosQuery<T> isNull() {
        addCondition(" IS NULL");
        return this;
    }

    public OneirosQuery<T> isNotNull() {
        addCondition(" IS NOT NULL");
        return this;
    }

    public OneirosQuery<T> and(String field) {
        this.currentField = field;
        return this;
    }

    public OneirosQuery<T> or(String field) {
        if (!whereClauses.isEmpty()) {
            whereClauses.add("__OR__");
        }
        this.currentField = field;
        return this;
    }

    private void addCondition(String operatorAndValue) {
        if (currentField == null) throw new IllegalStateException("Call .where() before adding a condition!");
        whereClauses.add(currentField + operatorAndValue);
        currentField = null;
    }

    // --- Field Selection ---

    /**
     * Omit specific fields from the result set.
     * Usage: .omit("password", "secretKey")
     * Supports nested fields: .omit("opts.security")
     */
    public OneirosQuery<T> omit(String... fields) {
        omitFields.addAll(Arrays.asList(fields));
        return this;
    }

    /**
     * Fetch related records (graph traversal).
     * Loads linked records directly with the query.
     */
    public OneirosQuery<T> fetch(String... fields) {
        fetchFields.addAll(Arrays.asList(fields));
        return this;
    }

    /**
     * Set a timeout for the query execution.
     * The query will be cancelled if it exceeds this duration.
     */
    public OneirosQuery<T> timeout(java.time.Duration duration) {
        long seconds = duration.getSeconds();
        long millis = duration.toMillis() % 1000;

        if (millis > 0) {
            this.timeoutClause = " TIMEOUT " + seconds + "s" + millis + "ms";
        } else {
            this.timeoutClause = " TIMEOUT " + seconds + "s";
        }
        return this;
    }

    /**
     * Enable parallel query execution for better performance on large datasets.
     */
    public OneirosQuery<T> parallel() {
        this.parallelEnabled = true;
        return this;
    }

    // --- Sorting & Limits ---

    public OneirosQuery<T> limit(int limit) {
        this.limitClause = " LIMIT " + limit;
        return this;
    }

    public OneirosQuery<T> offset(int offset) {
        this.offsetClause = " START " + offset;
        return this;
    }

    public OneirosQuery<T> orderBy(String field) {
        this.orderByClause = " ORDER BY " + field + " ASC";
        return this;
    }

    public OneirosQuery<T> orderByDesc(String field) {
        this.orderByClause = " ORDER BY " + field + " DESC";
        return this;
    }

    // --- Execution ---

    public Flux<T> execute(OneirosClient client) {
        String sql = buildSql();
        return client.query(sql, type);
    }

    public Mono<T> fetchOne(OneirosClient client) {
        limit(1);
        String sql = buildSql();
        return client.query(sql, type).next();
    }

    public String toSql() {
        return buildSql();
    }

    // --- Internals ---

    /**
     * Builds the complete SQL string in the correct SurrealQL syntax order.
     * <p>
     * Order: SELECT * [OMIT fields] FROM table [WHERE conditions] [ORDER BY]
     *        [LIMIT] [START] [FETCH fields] [TIMEOUT duration] [PARALLEL]
     */
    private String buildSql() {
        StringBuilder sql = new StringBuilder("SELECT * ");

        // OMIT clause kommt nach SELECT * aber vor FROM
        if (!omitFields.isEmpty()) {
            sql.append("OMIT ");
            sql.append(String.join(", ", omitFields));
            sql.append(" ");
        }

        sql.append("FROM ").append(tableName);

        // WHERE clause
        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ");

            for (int i = 0; i < whereClauses.size(); i++) {
                String clause = whereClauses.get(i);

                if (clause.equals("__OR__")) {
                    sql.append(" OR ");
                } else {
                    if (i > 0 && !whereClauses.get(i - 1).equals("__OR__")) {
                        sql.append(" AND ");
                    }
                    sql.append(clause);
                }
            }
        }

        // ORDER BY, LIMIT, START
        sql.append(orderByClause);
        sql.append(limitClause);
        sql.append(offsetClause);

        if (!fetchFields.isEmpty()) {
            sql.append(" FETCH ");
            sql.append(String.join(", ", fetchFields));
        }

        // TIMEOUT clause
        sql.append(timeoutClause);

        // PARALLEL flag
        if (parallelEnabled) {
            sql.append(" PARALLEL");
        }

        return sql.toString();
    }

    private String getTableName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(OneirosEntity.class)) {
            String val = clazz.getAnnotation(OneirosEntity.class).value();
            return val.isEmpty() ? clazz.getSimpleName().toLowerCase() : val;
        }
        return clazz.getSimpleName().toLowerCase();
    }

    private String formatValue(Object val) {
        if (val instanceof Number) return val.toString();
        if (val instanceof Boolean) return val.toString();
        return "'" + val.toString().replace("'", "\\'") + "'";
    }

    // ==================================================================================
    // STATEMENT API INTEGRATION
    // ==================================================================================

    /**
     * <h2>Statement API - Direct access to all SurrealQL statements</h2>
     *
     * <p>These methods provide seamless integration between the fluent query builder
     * and the complete Statement API, allowing you to combine both approaches.
     */

    // --- CREATE Statement ---

    /**
     * Create a new CREATE statement for a table.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.create(User.class)
     *     .set("name", "Alice")
     *     .set("email", "alice@example.com")
     *     .execute(client);
     * </pre>
     *
     * @param type the entity class
     * @return a new CreateStatement
     */
    public static <T> CreateStatement<T> create(Class<T> type) {
        return CreateStatement.table(type);
    }

    /**
     * Create a new CREATE statement for a specific record ID.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.create(User.class, "alice")
     *     .set("name", "Alice")
     *     .execute(client);
     * </pre>
     */
    public static <T> CreateStatement<T> create(Class<T> type, String id) {
        return CreateStatement.record(type, id);
    }

    // --- UPDATE Statement ---

    /**
     * Create a new UPDATE statement for a table.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.update(User.class)
     *     .set("verified", true)
     *     .where("age >= 18")
     *     .execute(client);
     * </pre>
     */
    public static <T> UpdateStatement<T> update(Class<T> type) {
        return UpdateStatement.table(type);
    }

    /**
     * Create a new UPDATE statement for a specific record ID.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.update(User.class, "user:alice")
     *     .set("verified", true)
     *     .execute(client);
     * </pre>
     */
    public static <T> UpdateStatement<T> update(Class<T> type, String id) {
        return UpdateStatement.record(type, id);
    }

    // --- DELETE Statement ---

    /**
     * Create a new DELETE statement for a table.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.delete(User.class)
     *     .where("age < 18")
     *     .execute(client);
     * </pre>
     */
    public static <T> DeleteStatement<T> delete(Class<T> type) {
        return DeleteStatement.from(type);
    }

    /**
     * Create a new DELETE statement for a specific record ID.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.delete(User.class, "user:bob")
     *     .execute(client);
     * </pre>
     */
    public static <T> DeleteStatement<T> delete(Class<T> type, String id) {
        return DeleteStatement.record(type, id);
    }

    // --- UPSERT Statement ---

    /**
     * Create a new UPSERT statement (insert-or-update).
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.upsert(User.class)
     *     .set("name", "Bob")
     *     .set("email", "bob@example.com")
     *     .where("email = 'bob@example.com'")
     *     .execute(client);
     * </pre>
     */
    public static <T> UpsertStatement<T> upsert(Class<T> type) {
        return UpsertStatement.table(type);
    }

    /**
     * Create a new UPSERT statement for a specific record ID.
     */
    public static <T> UpsertStatement<T> upsert(Class<T> type, String id) {
        return UpsertStatement.record(type, id);
    }

    // --- INSERT Statement ---

    /**
     * Create a new INSERT statement.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.insert(User.class)
     *     .fields("name", "email")
     *     .values("Charlie", "charlie@example.com")
     *     .onDuplicateKeyUpdate()
     *         .set("updated_at", "time::now()")
     *     .end()
     *     .execute(client);
     * </pre>
     */
    public static <T> InsertStatement<T> insert(Class<T> type) {
        return InsertStatement.into(type);
    }

    // --- RELATE Statement (Graph) ---

    /**
     * Create a new RELATE statement for graph relationships.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.relate("person:alice")
     *     .to("person:bob")
     *     .via("knows")
     *     .set("since", "2020-01-01")
     *     .execute(client);
     * </pre>
     */
    public static RelateStatement relate(String from) {
        return RelateStatement.from(from);
    }

    // --- Transaction Statement ---

    /**
     * Create a new transaction.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.transaction()
     *     .add(OneirosQuery.create(User.class).set("name", "Test"))
     *     .add(OneirosQuery.update(Account.class)
     *         .setRaw("balance -= 100")
     *         .where("user_id = user:test"))
     *     .commit(client);
     * </pre>
     */
    public static TransactionStatement transaction() {
        return TransactionStatement.begin();
    }

    // --- Conditional Statement (IF/ELSE) ---

    /**
     * Create a new IF statement for conditional logic.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.ifCondition("user.role = 'ADMIN'")
     *     .then(OneirosQuery.update(User.class)
     *         .set("permissions", "full"))
     *     .elseBlock()
     *     .then(OneirosQuery.throwError("Access denied"))
     *     .build()
     *     .execute(client);
     * </pre>
     */
    public static IfStatement ifCondition(String condition) {
        return IfStatement.condition(condition);
    }

    // --- FOR Loop Statement ---

    /**
     * Create a new FOR loop statement.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.forEach("$person", "(SELECT * FROM person WHERE age > 18)")
     *     .add(OneirosQuery.update(User.class, "$person.id")
     *         .set("can_vote", true))
     *     .execute(client);
     * </pre>
     */
    public static ForStatement forEach(String variable, String iterable) {
        return ForStatement.forEach(variable, iterable);
    }

    // --- LET Statement (Variables) ---

    /**
     * Create a new LET statement for variable assignment.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.let("user_id", "user:alice")
     * </pre>
     */
    public static LetStatement let(String name, String value) {
        return LetStatement.variable(name, value);
    }

    // --- Utility Statements ---

    /**
     * Create a THROW statement for error handling.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.throwError("Insufficient funds")
     * </pre>
     */
    public static ThrowStatement throwError(String message) {
        return ThrowStatement.error(message);
    }

    /**
     * Create a RETURN statement.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.returnValue("{ success: true }")
     * </pre>
     */
    public static ReturnStatement returnValue(String value) {
        return ReturnStatement.value(value);
    }

    /**
     * Create a SLEEP statement.
     *
     * <p>Example:
     * <pre>
     * OneirosQuery.sleep("5s").execute(client)
     * </pre>
     */
    public static SleepStatement sleep(String duration) {
        return SleepStatement.duration(duration);
    }

    /**
     * Create a BREAK statement (for loops).
     */
    public static BreakStatement breakLoop() {
        return BreakStatement.exit();
    }

    /**
     * Create a CONTINUE statement (for loops).
     */
    public static ContinueStatement continueLoop() {
        return ContinueStatement.skip();
    }

    // ==================================================================================
    // ADVANCED QUERY COMPOSITION
    // ==================================================================================

    /**
     * Convert this query builder into a SelectStatement for composition.
     *
     * <p>Allows using the fluent query builder inside transactions and other statements.
     *
     * <p>Example:
     * <pre>
     * var userQuery = OneirosQuery.select(User.class)
     *     .where("age").gte(18)
     *     .asStatement();
     *
     * OneirosQuery.transaction()
     *     .add(userQuery)
     *     .commit(client);
     * </pre>
     */
    public SelectStatement<T> asStatement() {
        SelectStatement<T> stmt = SelectStatement.from(type);

        // Build WHERE clause
        if (!whereClauses.isEmpty()) {
            StringBuilder whereStr = new StringBuilder();
            for (int i = 0; i < whereClauses.size(); i++) {
                String clause = whereClauses.get(i);
                if (clause.equals("__OR__")) {
                    whereStr.append(" OR ");
                } else {
                    if (i > 0 && !whereClauses.get(i - 1).equals("__OR__")) {
                        whereStr.append(" AND ");
                    }
                    whereStr.append(clause);
                }
            }
            stmt.where(whereStr.toString());
        }

        // Apply OMIT
        if (!omitFields.isEmpty()) {
            stmt.omit(omitFields.toArray(new String[0]));
        }

        // Apply FETCH
        if (!fetchFields.isEmpty()) {
            stmt.fetch(fetchFields.toArray(new String[0]));
        }

        // Apply ORDER BY (extract field and direction)
        if (!orderByClause.isEmpty()) {
            String orderBy = orderByClause.replace(" ORDER BY ", "");
            String[] parts = orderBy.split(" ");
            if (parts.length == 2) {
                // SelectStatement only takes field name, use orderBy() or orderByDesc()
                if ("DESC".equalsIgnoreCase(parts[1])) {
                    stmt.orderByDesc(parts[0]);
                } else {
                    stmt.orderBy(parts[0]);
                }
            }
        }

        // Apply LIMIT (with optional START)
        if (!limitClause.isEmpty()) {
            int limit = Integer.parseInt(limitClause.replace(" LIMIT ", ""));

            if (!offsetClause.isEmpty()) {
                int start = Integer.parseInt(offsetClause.replace(" START ", ""));
                stmt.limit(limit, start);
            } else {
                stmt.limit(limit);
            }
        }

        // Apply TIMEOUT (needs Duration, not String)
        if (!timeoutClause.isEmpty()) {
            // Parse the timeout string (e.g., "5s" or "5s100ms")
            String timeout = timeoutClause.replace(" TIMEOUT ", "");
            java.time.Duration duration = parseDuration(timeout);
            stmt.timeout(duration);
        }

        // Apply PARALLEL
        if (parallelEnabled) {
            stmt.parallel();
        }

        return stmt;
    }

    /**
     * Parse a SurrealQL duration string to Java Duration.
     * Supports: "5s", "100ms", "5s100ms", etc.
     */
    private java.time.Duration parseDuration(String durationStr) {
        long totalMillis = 0;

        // Parse seconds
        if (durationStr.contains("s")) {
            int sIndex = durationStr.indexOf("s");
            String secondsPart = durationStr.substring(0, sIndex);

            // Check if there's more after 's' (like "5s100ms")
            if (sIndex + 1 < durationStr.length()) {
                totalMillis += Long.parseLong(secondsPart) * 1000;

                // Parse milliseconds
                String remaining = durationStr.substring(sIndex + 1);
                if (remaining.endsWith("ms")) {
                    String millisPart = remaining.replace("ms", "");
                    totalMillis += Long.parseLong(millisPart);
                }
            } else {
                // Just seconds
                totalMillis = Long.parseLong(secondsPart) * 1000;
            }
        } else if (durationStr.contains("ms")) {
            // Just milliseconds
            String millisPart = durationStr.replace("ms", "");
            totalMillis = Long.parseLong(millisPart);
        }

        return java.time.Duration.ofMillis(totalMillis);
    }

    /**
     * Convenience method: execute as a Statement.
     *
     * <p>Internally converts to SelectStatement and executes.
     */
    public Flux<T> executeAsStatement(OneirosClient client) {
        return asStatement().execute(client);
    }
}


