package io.oneiros.statement.statements;

import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Transaction statement that wraps multiple statements in BEGIN/COMMIT.
 *
 * <p>This is a composite statement that executes multiple statements
 * atomically within a transaction context.
 *
 * <p><b>Example:</b>
 * <pre>
 * TransactionStatement.begin()
 *     .add(CreateStatement.table(User.class).set("name", "Alice"))
 *     .add(UpdateStatement.table(Account.class)
 *         .set("balance", 100)
 *         .where("user = user:alice"))
 *     .commit(client);
 * </pre>
 *
 * @since 1.0.0
 */
public class TransactionStatement implements Statement<Object> {

    private final List<Statement<?>> statements = new ArrayList<>();
    private String returnValue;
    private boolean shouldCancel = false;

    private TransactionStatement() {}

    /**
     * Start a new transaction.
     *
     * @return a new transaction statement
     */
    public static TransactionStatement begin() {
        return new TransactionStatement();
    }

    /**
     * Add a statement to the transaction.
     *
     * @param statement the statement to add
     * @return this transaction for chaining
     */
    public TransactionStatement add(Statement<?> statement) {
        statements.add(statement);
        return this;
    }

    /**
     * Add raw SQL to the transaction.
     *
     * @param sql the raw SQL statement
     * @return this transaction for chaining
     */
    public TransactionStatement addRaw(String sql) {
        statements.add(new RawStatement(sql));
        return this;
    }

    /**
     * Set a RETURN value for the transaction.
     *
     * @param expression the expression to return
     * @return this transaction for chaining
     */
    public TransactionStatement returnValue(String expression) {
        this.returnValue = expression;
        return this;
    }

    /**
     * Mark this transaction to be cancelled instead of committed.
     *
     * @return this transaction for chaining
     */
    public TransactionStatement cancel() {
        this.shouldCancel = true;
        return this;
    }

    @Override
    public String toSql() {
        return buildSql(!shouldCancel);
    }

    /**
     * Execute the transaction and commit.
     *
     * @param client the Oneiros client
     * @return a Mono that completes when the transaction is committed
     */
    public Mono<Void> commit(OneirosClient client) {
        String sql = buildSql(true);
        return client.query(sql, Object.class).then();
    }

    /**
     * Execute the transaction and cancel/rollback.
     *
     * @param client the Oneiros client
     * @return a Mono that completes when the transaction is cancelled
     */
    public Mono<Void> rollback(OneirosClient client) {
        String sql = buildSql(false);
        return client.query(sql, Object.class).then();
    }

    @Override
    public Flux<Object> execute(OneirosClient client) {
        return client.query(toSql(), Object.class);
    }

    @Override
    public Mono<Object> executeOne(OneirosClient client) {
        return execute(client).next();
    }

    // --- Private Helpers ---

    private String buildSql(boolean commit) {
        StringBuilder sql = new StringBuilder("BEGIN TRANSACTION;\n");

        for (Statement<?> statement : statements) {
            String stmtSql = statement.toSql();
            sql.append(stmtSql);
            if (!stmtSql.trim().endsWith(";")) {
                sql.append(";");
            }
            sql.append("\n");
        }

        if (returnValue != null) {
            sql.append("RETURN ").append(returnValue).append(";\n");
        }

        if (commit) {
            sql.append("COMMIT TRANSACTION;");
        } else {
            sql.append("CANCEL TRANSACTION;");
        }

        return sql.toString();
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
