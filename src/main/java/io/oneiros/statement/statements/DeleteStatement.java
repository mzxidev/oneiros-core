package io.oneiros.statement.statements;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import io.oneiros.statement.clause.WhereClause;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * DELETE statement for removing records.
 *
 * <p>Supports:
 * - WHERE conditions
 * - RETURN clauses
 * - TIMEOUT
 *
 * <p><b>Example:</b>
 * <pre>
 * DeleteStatement.from(User.class)
 *     .where("age < 18")
 *     .returnBefore()
 *     .execute(client);
 * </pre>
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

    /**
     * Add WHERE condition.
     *
     * @param condition the condition expression
     * @return this statement for chaining
     */
    public DeleteStatement<T> where(String condition) {
        whereClause.add(condition);
        return this;
    }

    /**
     * Add AND condition.
     *
     * @param condition the condition expression
     * @return this statement for chaining
     */
    public DeleteStatement<T> and(String condition) {
        whereClause.and(condition);
        return this;
    }

    /**
     * Add OR condition.
     *
     * @param condition the condition expression
     * @return this statement for chaining
     */
    public DeleteStatement<T> or(String condition) {
        whereClause.or(condition);
        return this;
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
        return client.query(toSql(), type);
    }

    @Override
    public Mono<T> executeOne(OneirosClient client) {
        return execute(client).next();
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
