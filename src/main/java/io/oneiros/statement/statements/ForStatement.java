package io.oneiros.statement.statements;

import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * FOR loop statement for iterating over arrays and ranges.
 *
 * <p>Supports:
 * - Arrays and ranges as iterables
 * - Nested statements inside the loop
 * - CONTINUE and BREAK
 *
 * <p><b>Example:</b>
 * <pre>
 * ForStatement.forEach("$person", "(SELECT * FROM person)")
 *     .add(UpdateStatement.record(User.class, "$person.id")
 *         .set("processed", true))
 *     .build();
 * </pre>
 *
 * @since 1.0.0
 */
public class ForStatement implements Statement<Object> {

    private final String variable;
    private final String iterable;
    private final List<Statement<?>> body = new ArrayList<>();

    private ForStatement(String variable, String iterable) {
        this.variable = variable;
        this.iterable = iterable;
    }

    /**
     * Create a FOR loop statement.
     *
     * @param variable  the loop variable (e.g. "$item")
     * @param iterable  the array or range to iterate over
     * @return a new FOR statement
     */
    public static ForStatement forEach(String variable, String iterable) {
        return new ForStatement(variable, iterable);
    }

    /**
     * Add a statement to the loop body.
     *
     * @param statement the statement to add
     * @return this FOR statement for chaining
     */
    public ForStatement add(Statement<?> statement) {
        body.add(statement);
        return this;
    }

    /**
     * Add raw SQL to the loop body.
     *
     * @param sql the raw SQL statement
     * @return this FOR statement for chaining
     */
    public ForStatement addRaw(String sql) {
        body.add(new RawStatement(sql));
        return this;
    }

    /**
     * Add a CONTINUE statement.
     *
     * @return this FOR statement for chaining
     */
    public ForStatement addContinue() {
        return addRaw("CONTINUE");
    }

    /**
     * Add a BREAK statement.
     *
     * @return this FOR statement for chaining
     */
    public ForStatement addBreak() {
        return addRaw("BREAK");
    }

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder("FOR ");
        sql.append(variable);
        sql.append(" IN ");
        sql.append(iterable);
        sql.append(" {\n");

        for (Statement<?> statement : body) {
            String stmtSql = statement.toSql();
            sql.append("  ").append(stmtSql);
            if (!stmtSql.trim().endsWith(";")) {
                sql.append(";");
            }
            sql.append("\n");
        }

        sql.append("}");
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

    /**
     * Internal raw statement wrapper.
     */
    private static class RawStatement implements Statement<Object> {
        private final String sql;

        RawStatement(String sql) {
            this.sql = sql;
        }

        @Override
        public String toSql() {
            return sql;
        }

        @Override
        public Flux<Object> execute(OneirosClient client) {
            return client.query(sql, Object.class);
        }

        @Override
        public Mono<Object> executeOne(OneirosClient client) {
            return execute(client).next();
        }
    }
}
