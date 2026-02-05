package io.oneiros.statement.statements;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import io.oneiros.statement.clause.WhereClause;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UPDATE statement for modifying existing records.
 *
 * <p>Supports:
 * - SET field = value syntax
 * - MERGE partial updates
 * - CONTENT full replacement
 * - WHERE conditions
 * - RETURN clauses
 * - TIMEOUT
 *
 * <p><b>Example:</b>
 * <pre>
 * UpdateStatement.table(User.class)
 *     .set("name", "Bob")
 *     .where("id = user:123")
 *     .returnAfter()
 *     .execute(client);
 * </pre>
 *
 * @param <T> the entity type
 * @since 1.0.0
 */
public class UpdateStatement<T> implements Statement<T> {

    private final Class<T> type;
    private final String target;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final WhereClause whereClause = new WhereClause();
    private String mergeContent;
    private String content;
    private String returnClause = "AFTER";
    private String timeoutClause;
    private boolean onlyOne = false;

    private UpdateStatement(Class<T> type, String target) {
        this.type = type;
        this.target = target;
    }

    /**
     * Start an UPDATE statement for a table.
     *
     * @param type the entity class
     * @return a new UPDATE statement
     */
    public static <T> UpdateStatement<T> table(Class<T> type) {
        return new UpdateStatement<>(type, getTableName(type));
    }

    /**
     * Start an UPDATE statement for a specific record ID.
     *
     * @param type the entity class
     * @param id   the record ID
     * @return a new UPDATE statement
     */
    public static <T> UpdateStatement<T> record(Class<T> type, String id) {
        return new UpdateStatement<>(type, getTableName(type) + ":" + id);
    }

    /**
     * Set a field value.
     *
     * @param field the field name
     * @param value the field value
     * @return this statement for chaining
     */
    public UpdateStatement<T> set(String field, Object value) {
        this.fields.put(field, value);
        return this;
    }

    /**
     * Set a field using raw expression (e.g. "balance += 100").
     *
     * @param expression the raw field expression
     * @return this statement for chaining
     */
    public UpdateStatement<T> setRaw(String expression) {
        this.fields.put(expression, null); // null = raw expression
        return this;
    }

    /**
     * Merge partial data using MERGE clause.
     *
     * @param content the content to merge
     * @return this statement for chaining
     */
    public UpdateStatement<T> merge(String content) {
        this.mergeContent = content;
        return this;
    }

    /**
     * Replace entire record using CONTENT clause.
     *
     * @param content the new content
     * @return this statement for chaining
     */
    public UpdateStatement<T> content(String content) {
        this.content = content;
        return this;
    }

    /**
     * Add WHERE condition.
     *
     * @param condition the condition expression
     * @return this statement for chaining
     */
    public UpdateStatement<T> where(String condition) {
        whereClause.add(condition);
        return this;
    }

    /**
     * Add AND condition.
     *
     * @param condition the condition expression
     * @return this statement for chaining
     */
    public UpdateStatement<T> and(String condition) {
        whereClause.and(condition);
        return this;
    }

    /**
     * Add OR condition.
     *
     * @param condition the condition expression
     * @return this statement for chaining
     */
    public UpdateStatement<T> or(String condition) {
        whereClause.or(condition);
        return this;
    }

    /**
     * Use ONLY keyword to return a single object instead of array.
     *
     * @return this statement for chaining
     */
    public UpdateStatement<T> only() {
        this.onlyOne = true;
        return this;
    }

    /**
     * Return nothing (RETURN NONE).
     *
     * @return this statement for chaining
     */
    public UpdateStatement<T> returnNone() {
        this.returnClause = "NONE";
        return this;
    }

    /**
     * Return record before changes (RETURN BEFORE).
     *
     * @return this statement for chaining
     */
    public UpdateStatement<T> returnBefore() {
        this.returnClause = "BEFORE";
        return this;
    }

    /**
     * Return record after changes (RETURN AFTER) - default.
     *
     * @return this statement for chaining
     */
    public UpdateStatement<T> returnAfter() {
        this.returnClause = "AFTER";
        return this;
    }

    /**
     * Return changeset diff (RETURN DIFF).
     *
     * @return this statement for chaining
     */
    public UpdateStatement<T> returnDiff() {
        this.returnClause = "DIFF";
        return this;
    }

    /**
     * Set query timeout.
     *
     * @param duration the timeout duration (e.g. "5s", "100ms")
     * @return this statement for chaining
     */
    public UpdateStatement<T> timeout(String duration) {
        this.timeoutClause = duration;
        return this;
    }

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder("UPDATE");

        if (onlyOne) {
            sql.append(" ONLY");
        }

        sql.append(" ").append(target);

        // SET clause
        if (!fields.isEmpty()) {
            sql.append(" SET ");
            boolean first = true;
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                if (!first) sql.append(", ");
                if (entry.getValue() == null) {
                    // Raw expression
                    sql.append(entry.getKey());
                } else {
                    sql.append(entry.getKey()).append(" = ");
                    sql.append(formatValue(entry.getValue()));
                }
                first = false;
            }
        }

        // MERGE clause
        if (mergeContent != null) {
            sql.append(" MERGE ").append(mergeContent);
        }

        // CONTENT clause
        if (content != null) {
            sql.append(" CONTENT ").append(content);
        }

        // WHERE clause
        if (!whereClause.isEmpty()) {
            sql.append(whereClause.toSql());
        }

        // RETURN clause
        if (returnClause != null && !returnClause.equals("AFTER")) {
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

    private String formatValue(Object value) {
        if (value == null) {
            return "NONE";
        } else if (value instanceof String) {
            return "'" + value.toString().replace("'", "\\'") + "'";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else {
            return "'" + value.toString().replace("'", "\\'") + "'";
        }
    }
}
