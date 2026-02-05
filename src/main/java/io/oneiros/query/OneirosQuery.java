package io.oneiros.query;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    // Hilfsvariable für den Builder-State
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

        // FETCH clause - lädt verknüpfte Records
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
}
