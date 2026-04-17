package io.oneiros.statement.statements;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import io.oneiros.statement.clause.WhereClause;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * DELETE statement for removing records with SQL injection protection.
 *
 * <p>Supports:
 * <ul>
 *   <li>WHERE conditions (parameterized by default)</li>
 *   <li>RETURN clauses</li>
 *   <li>TIMEOUT</li>
 * </ul>
 *
 * <p><b>🔐 Security:</b> All WHERE methods use parameterized queries by default.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * DeleteStatement.from(User.class)
 *     .where("status", "=", "inactive")  // Parameterized!
 *     .and("age", "<", 18)
 *     .returnBefore()
 *     .execute(client);
 * }</pre>
 *
 * @param <T> the entity type
 * @since 1.0.0
 */
public class DeleteStatement<T> implements Statement<T> {

    private final Class<T> type;
    private final String target;
    private final WhereClause whereClause = new WhereClause();
    private String returnClause;
    private String timeoutClause;
    private boolean onlyOne = false;

    private DeleteStatement(Class<T> type, String target) {
        this.type = type;
        this.target = target;
    }

    /**
     * Start a DELETE statement for a table.
     *
     * @param type the entity class
     * @return a new DELETE statement
     */
    public static <T> DeleteStatement<T> from(Class<T> type) {
        return new DeleteStatement<>(type, getTableName(type));
    }

    /**
     * Start a DELETE statement for a specific record ID.
     *
     * @param type the entity class
     * @param id   the record ID
     * @return a new DELETE statement
     */
    public static <T> DeleteStatement<T> record(Class<T> type, String id) {
        return new DeleteStatement<>(type, getTableName(type) + ":" + id);
    }

    // --- WHERE Clause (Parameterized - Default) ---

    /**
     * Adds a parameterized WHERE condition (SQL injection protected).
     *
     * <p>This is the recommended and default way to add WHERE conditions.
     *
     * <p>Example:
     * <pre>{@code
     * DeleteStatement.from(User.class)
     *     .where("email", "=", userInput)
     *     .execute(client);
     * }</pre>
     *
     * @param field the field name
     * @param operator the comparison operator ({@code =}, {@code !=}, {@code >}, {@code <}, {@code >=}, {@code <=}, LIKE, IN)
     * @param value the value (will be parameterized)
     * @return this statement for chaining
     */
    public DeleteStatement<T> where(String field, String operator, Object value) {
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
    public DeleteStatement<T> and(String field, String operator, Object value) {
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
    public DeleteStatement<T> or(String field, String operator, Object value) {
        whereClause.orSafe(field, operator, value);
        return this;
    }

    // --- WHERE Clause (Raw - Deprecated but backward-compatible) ---

    /**
     * Adds a raw WHERE condition (for backward compatibility).
     *
     * <p><b>⚠️ DEPRECATED:</b> Use {@link #where(String, String, Object)} instead.
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #where(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public DeleteStatement<T> where(String condition) {
        whereClause.add(condition);
        return this;
    }

    /**
     * Adds a raw WHERE condition (alias).
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #where(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public DeleteStatement<T> whereRaw(String condition) {
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
    public DeleteStatement<T> and(String condition) {
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
    public DeleteStatement<T> andRaw(String condition) {
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
    public DeleteStatement<T> or(String condition) {
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
    public DeleteStatement<T> orRaw(String condition) {
        whereClause.or(condition);
        return this;
    }

    // --- Legacy Safe Methods (now just aliases) ---

    /**
     * @deprecated Use {@link #where(String, String, Object)} instead - it's now the default
     */
    @Deprecated(since = "0.4.5")
    public DeleteStatement<T> whereSafe(String field, String operator, Object value) {
        return where(field, operator, value);
    }

    /**
     * @deprecated Use {@link #and(String, String, Object)} instead - it's now the default
     */
    @Deprecated(since = "0.4.5")
    public DeleteStatement<T> andSafe(String field, String operator, Object value) {
        return and(field, operator, value);
    }

    /**
     * @deprecated Use {@link #or(String, String, Object)} instead - it's now the default
     */
    @Deprecated(since = "0.4.5")
    public DeleteStatement<T> orSafe(String field, String operator, Object value) {
        return or(field, operator, value);
    }

    /**
     * Use ONLY keyword to return a single object instead of array.
     *
     * @return this statement for chaining
     */
    public DeleteStatement<T> only() {
        this.onlyOne = true;
        return this;
    }

    /**
     * Return nothing (RETURN NONE).
     *
     * @return this statement for chaining
     */
    public DeleteStatement<T> returnNone() {
        this.returnClause = "NONE";
        return this;
    }

    /**
     * Return record before deletion (RETURN BEFORE).
     *
     * @return this statement for chaining
     */
    public DeleteStatement<T> returnBefore() {
        this.returnClause = "BEFORE";
        return this;
    }

    /**
     * Return record after deletion (RETURN AFTER).
     *
     * @return this statement for chaining
     */
    public DeleteStatement<T> returnAfter() {
        this.returnClause = "AFTER";
        return this;
    }

    /**
     * Return changeset diff (RETURN DIFF).
     *
     * @return this statement for chaining
     */
    public DeleteStatement<T> returnDiff() {
        this.returnClause = "DIFF";
        return this;
    }

    /**
     * Set query timeout.
     *
     * @param duration the timeout duration (e.g. "5s", "100ms")
     * @return this statement for chaining
     */
    public DeleteStatement<T> timeout(String duration) {
        this.timeoutClause = duration;
        return this;
    }

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder("DELETE");

        if (onlyOne) {
            sql.append(" ONLY");
        }

        sql.append(" ").append(target);

        // WHERE clause
        if (!whereClause.isEmpty()) {
            sql.append(whereClause.toSql());
        }

        // RETURN clause
        if (returnClause != null) {
            sql.append(" RETURN ").append(returnClause);
        }

        // TIMEOUT clause
        if (timeoutClause != null) {
            sql.append(" TIMEOUT ").append(timeoutClause);
        }

        return sql.toString();
    }

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

    private static String getTableName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(OneirosEntity.class)) {
            String val = clazz.getAnnotation(OneirosEntity.class).value();
            return val.isEmpty() ? clazz.getSimpleName().toLowerCase() : val;
        }
        return clazz.getSimpleName().toLowerCase();
    }
}
