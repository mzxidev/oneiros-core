package io.oneiros.statement.statements;

import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * IF/ELSE conditional statement.
 *
 * <p>Supports:
 * - IF/ELSE IF/ELSE branches
 * - Nested statements in each branch
 * - Complex conditions
 *
 * <p><b>Example:</b>
 * <pre>
 * IfStatement.condition("user.role = 'ADMIN'")
 *     .then(UpdateStatement.table(User.class).set("permissions", "full"))
 *     .elseIf("user.role = 'USER'")
 *     .then(UpdateStatement.table(User.class).set("permissions", "limited"))
 *     .elseBlock()
 *     .then(new ThrowStatement("Invalid role"))
 *     .build();
 * </pre>
 *
 * @since 1.0.0
 */
public class IfStatement implements Statement<Object> {

    private final String condition;
    private final List<Statement<?>> thenBlock = new ArrayList<>();
    private final List<ElseIfBranch> elseIfBranches = new ArrayList<>();
    private final List<Statement<?>> elseBlock = new ArrayList<>();

    private IfStatement(String condition) {
        this.condition = condition;
    }

    /**
     * Create an IF statement with a condition.
     *
     * @param condition the IF condition
     * @return a new IF statement
     */
    public static IfStatement condition(String condition) {
        return new IfStatement(condition);
    }

    /**
     * Add a statement to the THEN block.
     *
     * @param statement the statement to add
     * @return this IF statement for chaining
     */
    public IfStatement then(Statement<?> statement) {
        thenBlock.add(statement);
        return this;
    }

    /**
     * Add raw SQL to the THEN block.
     *
     * @param sql the raw SQL statement
     * @return this IF statement for chaining
     */
    public IfStatement thenRaw(String sql) {
        thenBlock.add(new RawStatement(sql));
        return this;
    }

    /**
     * Start an ELSE IF branch.
     *
     * @param condition the ELSE IF condition
     * @return an ELSE IF builder
     */
    public ElseIfBuilder elseIf(String condition) {
        return new ElseIfBuilder(this, condition);
    }

    /**
     * Start the ELSE block.
     *
     * @return an ELSE builder
     */
    public ElseBuilder elseBlock() {
        return new ElseBuilder(this);
    }

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder("IF ");
        sql.append(condition);
        sql.append(" {\n");

        // THEN block
        for (Statement<?> statement : thenBlock) {
            String stmtSql = statement.toSql();
            sql.append("  ").append(stmtSql);
            if (!stmtSql.trim().endsWith(";")) {
                sql.append(";");
            }
            sql.append("\n");
        }

        sql.append("}");

        // ELSE IF branches
        for (ElseIfBranch branch : elseIfBranches) {
            sql.append(" ELSE IF ").append(branch.condition).append(" {\n");
            for (Statement<?> statement : branch.statements) {
                String stmtSql = statement.toSql();
                sql.append("  ").append(stmtSql);
                if (!stmtSql.trim().endsWith(";")) {
                    sql.append(";");
                }
                sql.append("\n");
            }
            sql.append("}");
        }

        // ELSE block
        if (!elseBlock.isEmpty()) {
            sql.append(" ELSE {\n");
            for (Statement<?> statement : elseBlock) {
                String stmtSql = statement.toSql();
                sql.append("  ").append(stmtSql);
                if (!stmtSql.trim().endsWith(";")) {
                    sql.append(";");
                }
                sql.append("\n");
            }
            sql.append("}");
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

    // --- Nested Classes ---

    /**
     * ELSE IF branch data.
     */
    private static class ElseIfBranch {
        final String condition;
        final List<Statement<?>> statements = new ArrayList<>();

        ElseIfBranch(String condition) {
            this.condition = condition;
        }
    }

    /**
     * ELSE IF builder.
     */
    public static class ElseIfBuilder {
        private final IfStatement parent;
        private final ElseIfBranch branch;

        ElseIfBuilder(IfStatement parent, String condition) {
            this.parent = parent;
            this.branch = new ElseIfBranch(condition);
            parent.elseIfBranches.add(branch);
        }

        /**
         * Add a statement to this ELSE IF block.
         *
         * @param statement the statement to add
         * @return this builder for chaining
         */
        public ElseIfBuilder then(Statement<?> statement) {
            branch.statements.add(statement);
            return this;
        }

        /**
         * Add raw SQL to this ELSE IF block.
         *
         * @param sql the raw SQL statement
         * @return this builder for chaining
         */
        public ElseIfBuilder thenRaw(String sql) {
            branch.statements.add(new RawStatement(sql));
            return this;
        }

        /**
         * Start another ELSE IF branch.
         *
         * @param condition the ELSE IF condition
         * @return a new ELSE IF builder
         */
        public ElseIfBuilder elseIf(String condition) {
            return parent.elseIf(condition);
        }

        /**
         * Start the ELSE block.
         *
         * @return an ELSE builder
         */
        public ElseBuilder elseBlock() {
            return parent.elseBlock();
        }

        /**
         * Finish the IF statement.
         *
         * @return the parent IF statement
         */
        public IfStatement build() {
            return parent;
        }

        /**
         * Generate SQL for the complete IF statement.
         * Convenience method that calls build().toSql().
         *
         * @return the SQL string
         */
        public String toSql() {
            return parent.toSql();
        }

        /**
         * Execute the complete IF statement.
         * Convenience method that calls build().execute().
         *
         * @param client the Oneiros client
         * @return a Flux of results
         */
        public Flux<Object> execute(OneirosClient client) {
            return parent.execute(client);
        }
    }

    /**
     * ELSE builder.
     */
    public static class ElseBuilder {
        private final IfStatement parent;

        ElseBuilder(IfStatement parent) {
            this.parent = parent;
        }

        /**
         * Add a statement to the ELSE block.
         *
         * @param statement the statement to add
         * @return this builder for chaining
         */
        public ElseBuilder then(Statement<?> statement) {
            parent.elseBlock.add(statement);
            return this;
        }

        /**
         * Add raw SQL to the ELSE block.
         *
         * @param sql the raw SQL statement
         * @return this builder for chaining
         */
        public ElseBuilder thenRaw(String sql) {
            parent.elseBlock.add(new RawStatement(sql));
            return this;
        }

        /**
         * Finish the IF statement.
         *
         * @return the parent IF statement
         */
        public IfStatement build() {
            return parent;
        }

        /**
         * Generate SQL for the complete IF statement.
         * Convenience method that calls build().toSql().
         *
         * @return the SQL string
         */
        public String toSql() {
            return parent.toSql();
        }

        /**
         * Execute the complete IF statement.
         * Convenience method that calls build().execute().
         *
         * @param client the Oneiros client
         * @return a Flux of results
         */
        public Flux<Object> execute(OneirosClient client) {
            return parent.execute(client);
        }
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
