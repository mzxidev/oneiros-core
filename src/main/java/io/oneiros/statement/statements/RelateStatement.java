package io.oneiros.statement.statements;

import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RELATE statement for creating graph relationships.
 *
 * <p>Supports:
 * - Graph edge creation between records
 * - SET field = value syntax
 * - CONTENT syntax
 * - RETURN clauses
 *
 * <p><b>Example:</b>
 * <pre>
 * RelateStatement.from("person:alice")
 *     .to("person:bob")
 *     .via("knows")
 *     .set("since", "2020-01-01")
 *     .execute(client);
 * </pre>
 *
 * @since 1.0.0
 */
public class RelateStatement implements Statement<Object> {

    private final String from;
    private String to;
    private String edgeTable;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private String content;
    private String returnClause = "AFTER";
    private String timeoutClause;
    private boolean onlyOne = false;

    private RelateStatement(String from) {
        this.from = from;
    }

    /**
     * Start a RELATE statement from a record.
     *
     * @param from the source record
     * @return a new RELATE statement
     */
    public static RelateStatement from(String from) {
        return new RelateStatement(from);
    }

    /**
     * Specify the target record.
     *
     * @param to the target record
     * @return this statement for chaining
     */
    public RelateStatement to(String to) {
        this.to = to;
        return this;
    }

    /**
     * Specify the edge table name.
     *
     * @param edgeTable the edge table name
     * @return this statement for chaining
     */
    public RelateStatement via(String edgeTable) {
        this.edgeTable = edgeTable;
        return this;
    }

    /**
     * Set a field value on the edge.
     *
     * @param field the field name
     * @param value the field value
     * @return this statement for chaining
     */
    public RelateStatement set(String field, Object value) {
        this.fields.put(field, value);
        return this;
    }

    /**
     * Set edge data using CONTENT clause.
     *
     * @param content the content object as SurrealQL
     * @return this statement for chaining
     */
    public RelateStatement content(String content) {
        this.content = content;
        return this;
    }

    /**
     * Use ONLY keyword to return a single object instead of array.
     *
     * @return this statement for chaining
     */
    public RelateStatement only() {
        this.onlyOne = true;
        return this;
    }

    /**
     * Return nothing (RETURN NONE).
     *
     * @return this statement for chaining
     */
    public RelateStatement returnNone() {
        this.returnClause = "NONE";
        return this;
    }

    /**
     * Return record before changes (RETURN BEFORE).
     *
     * @return this statement for chaining
     */
    public RelateStatement returnBefore() {
        this.returnClause = "BEFORE";
        return this;
    }

    /**
     * Return record after changes (RETURN AFTER) - default.
     *
     * @return this statement for chaining
     */
    public RelateStatement returnAfter() {
        this.returnClause = "AFTER";
        return this;
    }

    /**
     * Return changeset diff (RETURN DIFF).
     *
     * @return this statement for chaining
     */
    public RelateStatement returnDiff() {
        this.returnClause = "DIFF";
        return this;
    }

    /**
     * Set query timeout.
     *
     * @param duration the timeout duration (e.g. "5s", "100ms")
     * @return this statement for chaining
     */
    public RelateStatement timeout(String duration) {
        this.timeoutClause = duration;
        return this;
    }

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder("RELATE");

        if (onlyOne) {
            sql.append(" ONLY");
        }

        sql.append(" ").append(from);
        sql.append("->").append(edgeTable);
        sql.append("->").append(to);

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
    public Flux<Object> execute(OneirosClient client) {
        return client.query(toSql(), Object.class);
    }

    @Override
    public Mono<Object> executeOne(OneirosClient client) {
        return execute(client).next();
    }

    // --- Helpers ---

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
