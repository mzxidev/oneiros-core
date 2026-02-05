package io.oneiros.statement;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import io.oneiros.statement.clause.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * SELECT statement with full clause support.
 *
 * Supports all SurrealQL clauses:
 * - WHERE, GROUP BY, ORDER BY, LIMIT
 * - FETCH, OMIT, SPLIT, TIMEOUT, PARALLEL, EXPLAIN
 *
 * Example:
 * <pre>
 * SelectStatement.from(User.class)
 *     .where("role = 'ADMIN'")
 *     .omit("password")
 *     .fetch("profile")
 *     .orderBy("name")
 *     .limit(10)
 *     .execute(client);
 * </pre>
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

    // --- WHERE Clause ---

    public SelectStatement<T> where(String condition) {
        whereClause.add(condition);
        return this;
    }

    public SelectStatement<T> and(String condition) {
        whereClause.and(condition);
        return this;
    }

    public SelectStatement<T> or(String condition) {
        whereClause.or(condition);
        return this;
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
        return client.query(sql, type);
    }

    @Override
    public Mono<T> executeOne(OneirosClient client) {
        String sql = toSql();
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
