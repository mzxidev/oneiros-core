package io.oneiros.statement.statements;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CREATE statement for inserting new records.
 *
 * <p>Supports:
 * - SET field = value syntax
 * - CONTENT object syntax
 * - RETURN clauses (NONE, BEFORE, AFTER, DIFF)
 * - TIMEOUT
 *
 * <p><b>Example:</b>
 * <pre>
 * CreateStatement.table(User.class)
 *     .set("name", "Alice")
 *     .set("email", "alice@example.com")
 *     .returnAfter()
 *     .execute(client);
 * </pre>
 *
 * @param <T> the entity type
 * @since 1.0.0
 */
public class CreateStatement<T> implements Statement<T> {

    private final Class<T> type;
    private final String target;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private String content;
    private String returnClause = "AFTER";
    private String timeoutClause;
    private boolean onlyOne = false;

    private CreateStatement(Class<T> type, String target) {
        this.type = type;
        this.target = target;
    }

    /**
     * Start a CREATE statement for a table.
     *
     * @param type the entity class
     * @return a new CREATE statement
     */
    public static <T> CreateStatement<T> table(Class<T> type) {
        return new CreateStatement<>(type, getTableName(type));
    }

    /**
     * Start a CREATE statement for a specific record ID.
     *
     * @param type the entity class
     * @param id   the record ID
     * @return a new CREATE statement
     */
    public static <T> CreateStatement<T> record(Class<T> type, String id) {
        return new CreateStatement<>(type, getTableName(type) + ":" + id);
    }

    /**
     * Set a field value.
     *
     * @param field the field name
     * @param value the field value
     * @return this statement for chaining
     */
    public CreateStatement<T> set(String field, Object value) {
        this.fields.put(field, value);
        return this;
    }

    /**
     * Set record data using CONTENT clause.
     *
     * @param content the content object as SurrealQL
     * @return this statement for chaining
     */
    public CreateStatement<T> content(String content) {
        this.content = content;
        return this;
    }

    /**
     * Use ONLY keyword to return a single object instead of array.
     *
     * @return this statement for chaining
     */
    public CreateStatement<T> only() {
        this.onlyOne = true;
        return this;
    }

    /**
     * Return nothing (RETURN NONE).
     *
     * @return this statement for chaining
     */
    public CreateStatement<T> returnNone() {
        this.returnClause = "NONE";
        return this;
    }

    /**
     * Return record before changes (RETURN BEFORE).
     *
     * @return this statement for chaining
     */
    public CreateStatement<T> returnBefore() {
        this.returnClause = "BEFORE";
        return this;
    }

    /**
     * Return record after changes (RETURN AFTER) - default.
     *
     * @return this statement for chaining
     */
    public CreateStatement<T> returnAfter() {
        this.returnClause = "AFTER";
        return this;
    }

    /**
     * Return changeset diff (RETURN DIFF).
     *
     * @return this statement for chaining
     */
    public CreateStatement<T> returnDiff() {
        this.returnClause = "DIFF";
        return this;
    }

    /**
     * Set query timeout.
     *
     * @param duration the timeout duration (e.g. "5s", "100ms")
     * @return this statement for chaining
     */
    public CreateStatement<T> timeout(String duration) {
        this.timeoutClause = duration;
        return this;
    }

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder("CREATE");

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
                sql.append(entry.getKey()).append(" = ");
                sql.append(formatValue(entry.getValue()));
                first = false;
            }
        }

        // CONTENT clause
        if (content != null) {
            sql.append(" CONTENT ").append(content);
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
