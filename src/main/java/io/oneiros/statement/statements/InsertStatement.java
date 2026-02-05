package io.oneiros.statement.statements;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * INSERT statement for inserting records.
 *
 * <p>Supports:
 * - Single and bulk inserts
 * - VALUES syntax
 * - CONTENT syntax
 * - ON DUPLICATE KEY UPDATE
 * - RETURN clauses
 *
 * <p><b>Example:</b>
 * <pre>
 * InsertStatement.into(User.class)
 *     .values("name", "Alice", "email", "alice@example.com")
 *     .onDuplicateKeyUpdate()
 *         .set("updated_at", "time::now()")
 *     .execute(client);
 * </pre>
 *
 * @param <T> the entity type
 * @since 1.0.0
 */
public class InsertStatement<T> implements Statement<T> {

    private final Class<T> type;
    private final String target;
    private final List<String> fields = new ArrayList<>();
    private final List<List<Object>> valuesList = new ArrayList<>();
    private String content;
    private String onDuplicateUpdate;
    private String returnClause;
    private boolean ignoreErrors = false;
    private boolean isRelation = false;

    private InsertStatement(Class<T> type, String target) {
        this.type = type;
        this.target = target;
    }

    /**
     * Start an INSERT statement for a table.
     *
     * @param type the entity class
     * @return a new INSERT statement
     */
    public static <T> InsertStatement<T> into(Class<T> type) {
        return new InsertStatement<>(type, getTableName(type));
    }

    /**
     * Mark as INSERT RELATION.
     *
     * @return this statement for chaining
     */
    public InsertStatement<T> relation() {
        this.isRelation = true;
        return this;
    }

    /**
     * Use IGNORE clause to silently ignore duplicates.
     *
     * @return this statement for chaining
     */
    public InsertStatement<T> ignore() {
        this.ignoreErrors = true;
        return this;
    }

    /**
     * Specify field names for VALUES syntax.
     *
     * @param fieldNames the field names
     * @return this statement for chaining
     */
    public InsertStatement<T> fields(String... fieldNames) {
        this.fields.clear();
        for (String field : fieldNames) {
            this.fields.add(field);
        }
        return this;
    }

    /**
     * Add values for the fields.
     *
     * @param values the values (must match field count)
     * @return this statement for chaining
     */
    public InsertStatement<T> values(Object... values) {
        List<Object> valueRow = new ArrayList<>();
        for (Object value : values) {
            valueRow.add(value);
        }
        valuesList.add(valueRow);
        return this;
    }

    /**
     * Set record data using CONTENT clause.
     *
     * @param content the content object as SurrealQL
     * @return this statement for chaining
     */
    public InsertStatement<T> content(String content) {
        this.content = content;
        return this;
    }

    /**
     * Start ON DUPLICATE KEY UPDATE clause.
     *
     * @return a builder for the duplicate key update
     */
    public DuplicateKeyUpdateBuilder<T> onDuplicateKeyUpdate() {
        return new DuplicateKeyUpdateBuilder<>(this);
    }

    /**
     * Return nothing (RETURN NONE).
     *
     * @return this statement for chaining
     */
    public InsertStatement<T> returnNone() {
        this.returnClause = "NONE";
        return this;
    }

    /**
     * Return record after insert (RETURN AFTER).
     *
     * @return this statement for chaining
     */
    public InsertStatement<T> returnAfter() {
        this.returnClause = "AFTER";
        return this;
    }

    /**
     * Return changeset diff (RETURN DIFF).
     *
     * @return this statement for chaining
     */
    public InsertStatement<T> returnDiff() {
        this.returnClause = "DIFF";
        return this;
    }

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder("INSERT");

        if (isRelation) {
            sql.append(" RELATION");
        }

        if (ignoreErrors) {
            sql.append(" IGNORE");
        }

        sql.append(" INTO ").append(target);

        // CONTENT clause
        if (content != null) {
            sql.append(" ").append(content);
        }

        // VALUES clause
        if (!valuesList.isEmpty()) {
            sql.append(" (").append(String.join(", ", fields)).append(")");
            sql.append(" VALUES ");

            boolean firstRow = true;
            for (List<Object> values : valuesList) {
                if (!firstRow) sql.append(", ");
                sql.append("(");
                boolean firstVal = true;
                for (Object value : values) {
                    if (!firstVal) sql.append(", ");
                    sql.append(formatValue(value));
                    firstVal = false;
                }
                sql.append(")");
                firstRow = false;
            }
        }

        // ON DUPLICATE KEY UPDATE
        if (onDuplicateUpdate != null) {
            sql.append(" ").append(onDuplicateUpdate);
        }

        // RETURN clause
        if (returnClause != null) {
            sql.append(" RETURN ").append(returnClause);
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

    // --- Nested Builder ---

    /**
     * Builder for ON DUPLICATE KEY UPDATE clause.
     */
    public static class DuplicateKeyUpdateBuilder<T> {
        private final InsertStatement<T> parent;
        private final StringBuilder updateClause = new StringBuilder("ON DUPLICATE KEY UPDATE ");

        DuplicateKeyUpdateBuilder(InsertStatement<T> parent) {
            this.parent = parent;
        }

        /**
         * Set a field in the update clause.
         *
         * @param field the field name
         * @param value the value
         * @return this builder for chaining
         */
        public DuplicateKeyUpdateBuilder<T> set(String field, Object value) {
            if (updateClause.length() > "ON DUPLICATE KEY UPDATE ".length()) {
                updateClause.append(", ");
            }
            updateClause.append(field).append(" = ").append(formatValue(value));
            return this;
        }

        /**
         * Complete the duplicate key update and return to parent.
         *
         * @return the parent INSERT statement
         */
        public InsertStatement<T> end() {
            parent.onDuplicateUpdate = updateClause.toString();
            return parent;
        }

        private String formatValue(Object value) {
            if (value == null) return "NONE";
            if (value instanceof String) {
                return "'" + value.toString().replace("'", "\\'") + "'";
            }
            return value.toString();
        }
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
