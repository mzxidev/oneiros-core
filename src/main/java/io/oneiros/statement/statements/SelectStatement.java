package io.oneiros.statement.statements;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import io.oneiros.statement.clause.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SELECT statement with full clause support and SQL injection protection.
 *
 * <p>Supports all SurrealQL clauses:
 * <ul>
 *   <li>WHERE, GROUP BY, ORDER BY, LIMIT</li>
 *   <li>FETCH, OMIT, SPLIT, TIMEOUT, PARALLEL, EXPLAIN</li>
 * </ul>
 *
 * <p><b>🔐 Security:</b> All WHERE methods use parameterized queries by default.
 *
 * <p>Example:
 * <pre>{@code
 * // Safe parameterized query (recommended)
 * SelectStatement.from(User.class)
 *     .where("email", "=", userInput)      // Parameterized!
 *     .and("status", "=", "active")
 *     .omit("password")
 *     .fetch("profile")
 *     .orderBy("name")
 *     .limit(10)
 *     .execute(client);
 * }</pre>
 */
public class SelectStatement<T> implements Statement<T> {

    private final Class<T> type;
    private final String tableName;

    // Clauses
    private final WhereClause whereClause = new WhereClause();
    private final GroupByClause groupByClause = new GroupByClause();
    private final List<OrderByClause> orderByClauses = new ArrayList<>();
    private LimitClause limitClause;
    private final FetchClause fetchClause = new FetchClause();
    private final OmitClause omitClause = new OmitClause();
    private final SplitClause splitClause = new SplitClause();
    private TimeoutClause timeoutClause;
    private ParallelClause parallelClause;
    private ExplainClause explainClause;
    private VersionClause versionClause;

    // Projection
    private String projection = "*";

    private SelectStatement(Class<T> type) {
        this.type = type;
        this.tableName = getTableName(type);
    }

    /**
     * Start a SELECT statement.
     */
    public static <T> SelectStatement<T> from(Class<T> type) {
        return new SelectStatement<>(type);
    }

    /**
     * Specify fields to select.
     */
    public SelectStatement<T> select(String... fields) {
        this.projection = String.join(", ", fields);
        return this;
    }

    // --- WHERE Clause (Parameterized - Default) ---

    /**
     * Adds a parameterized WHERE condition (SQL injection protected).
     *
     * <p>This is the recommended and default way to add WHERE conditions.
     *
     * <p>Example:
     * <pre>{@code
     * SelectStatement.from(User.class)
     *     .where("email", "=", userInput)
     *     .execute(client);
     * }</pre>
     *
     * @param field the field name
     * @param operator the comparison operator (=, !=, >, <, >=, <=, LIKE, IN)
     * @param value the value (will be parameterized)
     * @return this statement for chaining
     */
    public SelectStatement<T> where(String field, String operator, Object value) {
        whereClause.addSafe(field, operator, value);
        return this;
    }

    /**
     * Adds a parameterized AND condition.
     *
     * @param field the field name
     * @param operator the comparison operator
     * @param value the value (will be parameterized)
     * @return this statement for chaining
     */
    public SelectStatement<T> and(String field, String operator, Object value) {
        whereClause.andSafe(field, operator, value);
        return this;
    }

    /**
     * Adds a parameterized OR condition.
     *
     * @param field the field name
     * @param operator the comparison operator
     * @param value the value (will be parameterized)
     * @return this statement for chaining
     */
    public SelectStatement<T> or(String field, String operator, Object value) {
        whereClause.orSafe(field, operator, value);
        return this;
    }

    // --- WHERE Clause (Raw - Deprecated but backward-compatible) ---

    /**
     * Adds a raw WHERE condition (for backward compatibility).
     *
     * <p><b>⚠️ DEPRECATED:</b> Use {@link #where(String, String, Object)} instead.
     * This method is vulnerable to SQL injection if user input is concatenated.
     *
     * @param condition the raw condition (e.g., "age > 18")
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #where(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public SelectStatement<T> where(String condition) {
        whereClause.add(condition);
        return this;
    }

    /**
     * Adds a raw WHERE condition (alias for backward compatibility).
     *
     * @param condition the raw condition (e.g., "age > 18")
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #where(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public SelectStatement<T> whereRaw(String condition) {
        whereClause.add(condition);
        return this;
    }

    /**
     * Adds a raw AND condition (for backward compatibility).
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #and(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public SelectStatement<T> and(String condition) {
        whereClause.and(condition);
        return this;
    }

    /**
     * Adds a raw AND condition (alias).
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #and(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public SelectStatement<T> andRaw(String condition) {
        whereClause.and(condition);
        return this;
    }

    /**
     * Adds a raw OR condition (for backward compatibility).
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #or(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public SelectStatement<T> or(String condition) {
        whereClause.or(condition);
        return this;
    }

    /**
     * Adds a raw OR condition (alias).
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #or(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public SelectStatement<T> orRaw(String condition) {
        whereClause.or(condition);
        return this;
    }

    // --- Legacy Safe Methods (now just aliases) ---

    /**
     * @deprecated Use {@link #where(String, String, Object)} instead - it's now the default
     */
    @Deprecated(since = "0.4.5")
    public SelectStatement<T> whereSafe(String field, String operator, Object value) {
        return where(field, operator, value);
    }

    /**
     * @deprecated Use {@link #and(String, String, Object)} instead - it's now the default
     */
    @Deprecated(since = "0.4.5")
    public SelectStatement<T> andSafe(String field, String operator, Object value) {
        return and(field, operator, value);
    }

    /**
     * @deprecated Use {@link #or(String, String, Object)} instead - it's now the default
     */
    @Deprecated(since = "0.4.5")
    public SelectStatement<T> orSafe(String field, String operator, Object value) {
        return or(field, operator, value);
    }

    // --- GROUP BY Clause ---

    public SelectStatement<T> groupBy(String... fields) {
        for (String field : fields) {
            groupByClause.add(field);
        }
        return this;
    }

    // --- ORDER BY Clause ---

    public SelectStatement<T> orderBy(String field) {
        orderByClauses.add(new OrderByClause(field, "ASC"));
        return this;
    }

    public SelectStatement<T> orderByDesc(String field) {
        orderByClauses.add(new OrderByClause(field, "DESC"));
        return this;
    }

    /**
     * Enables Time-Travel-Debugging for this query.
     * Selects data as it existed at the specified point in time.
     * 
     * @param timestamp the point in time
     * @return this statement for chaining
     */
    public SelectStatement<T> at(Instant timestamp) {
        this.versionClause = new VersionClause(timestamp);
        return this;
    }

    // --- LIMIT Clause ---

    public SelectStatement<T> limit(int limit) {
        this.limitClause = new LimitClause(limit);
        return this;
    }

    public SelectStatement<T> limit(int limit, int start) {
        this.limitClause = new LimitClause(limit).start(start);
        return this;
    }

    // --- FETCH Clause ---

    public SelectStatement<T> fetch(String... fields) {
        for (String field : fields) {
            fetchClause.add(field);
        }
        return this;
    }

    // --- OMIT Clause ---

    public SelectStatement<T> omit(String... fields) {
        for (String field : fields) {
            omitClause.add(field);
        }
        return this;
    }

    // --- SPLIT Clause ---

    public SelectStatement<T> split(String... fields) {
        for (String field : fields) {
            splitClause.add(field);
        }
        return this;
    }

    // --- TIMEOUT Clause ---

    public SelectStatement<T> timeout(Duration duration) {
        this.timeoutClause = new TimeoutClause(duration);
        return this;
    }

    // --- PARALLEL Clause ---

    public SelectStatement<T> parallel() {
        this.parallelClause = new ParallelClause();
        return this;
    }

    // --- EXPLAIN Clause ---

    public SelectStatement<T> explain() {
        this.explainClause = new ExplainClause();
        return this;
    }

    public SelectStatement<T> explainFull() {
        this.explainClause = new ExplainClause(true);
        return this;
    }

    // --- SQL Building ---

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder();

        // EXPLAIN comes first
        if (explainClause != null) {
            sql.append(explainClause.toSql().trim()).append(" ");
        }

        // SELECT projection
        sql.append("SELECT ").append(projection);

        // OMIT
        if (!omitClause.isEmpty()) {
            sql.append(omitClause.toSql());
        }

        // FROM
        sql.append(" FROM ").append(tableName);

        // VERSION (Time-Travel)
        if (versionClause != null) {
            sql.append(versionClause.toSql());
        }

        // WHERE
        if (!whereClause.isEmpty()) {
            sql.append(whereClause.toSql());
        }

        // SPLIT
        if (!splitClause.isEmpty()) {
            sql.append(splitClause.toSql());
        }

        // GROUP BY
        if (!groupByClause.isEmpty()) {
            sql.append(groupByClause.toSql());
        }

        // ORDER BY
        for (OrderByClause orderBy : orderByClauses) {
            sql.append(orderBy.toSql());
        }

        // LIMIT
        if (limitClause != null) {
            sql.append(limitClause.toSql());
        }

        // FETCH
        if (!fetchClause.isEmpty()) {
            sql.append(fetchClause.toSql());
        }

        // TIMEOUT
        if (timeoutClause != null) {
            sql.append(timeoutClause.toSql());
        }

        // PARALLEL
        if (parallelClause != null) {
            sql.append(parallelClause.toSql());
        }

        return sql.toString();
    }

    // --- Execution ---

    @Override
    public Flux<T> execute(OneirosClient client) {
        String sql = toSql();
        // Use parameterized query if WHERE has parameters
        if (whereClause.hasParameters()) {
            return client.query(sql, whereClause.getParameters(), type);
        }
        return client.query(sql, type);
    }

    @Override
    public Mono<T> executeOne(OneirosClient client) {
        String sql = toSql();
        // Use parameterized query if WHERE has parameters
        if (whereClause.hasParameters()) {
            return client.query(sql, whereClause.getParameters(), type).next();
        }
        return client.query(sql, type).next();
    }

    // --- Helpers ---

    private String getTableName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(OneirosEntity.class)) {
            String val = clazz.getAnnotation(OneirosEntity.class).value();
            return val.isEmpty() ? clazz.getSimpleName().toLowerCase() : val;
        }
        return clazz.getSimpleName().toLowerCase();
    }
}
