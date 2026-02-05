package io.oneiros.transaction;

import io.oneiros.client.OneirosClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent API Builder for SurrealDB transactions with full ACID guarantees.
 * <p>
 * Supports:
 * - CREATE, UPDATE, DELETE, SELECT with clauses
 * - IF/ELSE conditions (structured fluent API)
 * - THROW, RETURN, LET
 * - All clauses: WHERE, FETCH, OMIT, LIMIT, TIMEOUT
 * <p>
 * Example:
 * <pre>
 * // Fluent API Transaction
 * TransactionBuilder.begin()
 *     .create("account:one").set("balance", 1000).end()
 *     .create("account:two").set("balance", 500).end()
 *     .ifCondition()
 *         .field("account:two.balance").lt(100)
 *         .then().throwError("Insufficient funds!").endIf()
 *     .update("account:one").set("balance -= 100").end()
 *     .update("account:two").set("balance += 100").end()
 *     .commit(client);
 * </pre>
 */
public class TransactionBuilder {

    private final List<String> statements = new ArrayList<>();
    private String returnStatement;
    private boolean willCancel = false;

    TransactionBuilder() {} // package-private für Proxy-Klassen

    public static TransactionBuilder begin() {
        return new TransactionBuilder();
    }

    // --- Statement Builders ---

    /**
     * Create a CREATE statement.
     *
     * @param target the table or record to create
     * @return a CREATE statement builder
     */
    public CreateStatementBuilder create(String target) {
        return new CreateStatementBuilder(this, target);
    }

    /**
     * Create an UPDATE statement.
     *
     * @param target the table or record to update
     * @return an UPDATE statement builder
     */
    public UpdateStatementBuilder update(String target) {
        return new UpdateStatementBuilder(this, target);
    }

    /**
     * Create a DELETE statement.
     *
     * @param target the table or record to delete
     * @return a DELETE statement builder
     */
    public DeleteStatementBuilder delete(String target) {
        return new DeleteStatementBuilder(this, target);
    }

    /**
     * Create a SELECT statement.
     *
     * @param fields the fields to select
     * @return a SELECT statement builder
     */
    public SelectStatementBuilder select(String... fields) {
        return new SelectStatementBuilder(this, fields);
    }

    /**
     * Create a FOR loop to iterate over arrays or ranges.
     *
     * <p><b>Example:</b>
     * <pre>
     * .forEach("$person", "(SELECT * FROM person)")
     *     .update("$person.id").set("processed", "true").endUpdate()
     * .endFor()
     * </pre>
     *
     * @param variable  the loop variable (e.g. "$person")
     * @param iterable  the array or range to iterate over
     * @return a FOR loop builder
     */
    public ForLoopBuilder forEach(String variable, String iterable) {
        return new ForLoopBuilder(this, variable, iterable);
    }

    /**
     * Add a raw SurrealQL statement (internal use).
     *
     * @param statement the statement to add
     */
    void addRawStatement(String statement) {
        statements.add(statement);
    }

    public TransactionBuilder let(String variable, String expression) {
        statements.add("LET $" + variable + " = " + expression + ";");
        return this;
    }

    public IfConditionBuilder ifCondition() {
        return new IfConditionBuilder(this);
    }

    public TransactionBuilder throwError(String errorMessage) {
        statements.add("THROW \"" + errorMessage + "\";");
        return this;
    }

    /**
     * Add a RETURN statement to set the transaction output.
     *
     * <p><b>Example:</b>
     * <pre>
     * .returnValue("'Transaction successful'")
     * .returnValue("$result")
     * </pre>
     *
     * @param expression the value or expression to return
     * @return this builder for chaining
     */
    public TransactionBuilder returnValue(String expression) {
        this.returnStatement = "RETURN " + expression + ";";
        return this;
    }

    /**
     * Add a SLEEP statement to pause execution.
     *
     * <p><b>Example:</b>
     * <pre>
     * .sleep("100ms")  // Sleep 100 milliseconds
     * .sleep("5s")     // Sleep 5 seconds
     * </pre>
     *
     * @param duration the sleep duration (e.g. "100ms", "5s")
     * @return this builder for chaining
     */
    public TransactionBuilder sleep(String duration) {
        statements.add("SLEEP " + duration + ";");
        return this;
    }

    /**
     * Add a CONTINUE statement (for use inside FOR loops).
     *
     * <p><b>Note:</b> This should only be used inside a FOR loop context.
     * Calling outside a loop will result in a runtime error.
     *
     * @return this builder for chaining
     */
    public TransactionBuilder continueStatement() {
        statements.add("CONTINUE;");
        return this;
    }

    /**
     * Add a BREAK statement (for use inside FOR loops).
     *
     * <p><b>Note:</b> This should only be used inside a FOR loop context.
     * Calling outside a loop will result in a runtime error.
     *
     * @return this builder for chaining
     */
    public TransactionBuilder breakStatement() {
        statements.add("BREAK;");
        return this;
    }

    // --- Raw SQL (backwards compatibility) ---

    public TransactionBuilder add(String statement) {
        statements.add(statement);
        return this;
    }

    public TransactionBuilder addAll(String... statements) {
        this.statements.addAll(Arrays.asList(statements));
        return this;
    }

    public TransactionBuilder addIf(String condition, String errorMessage) {
        statements.add("IF " + condition + " { THROW \"" + errorMessage + "\"; };");
        return this;
    }

    public TransactionBuilder markForCancel() {
        this.willCancel = true;
        return this;
    }

    // --- Execution ---

    public Mono<List<Object>> commit(OneirosClient client) {
        String sql = buildSql(true);
        return client.query(sql, Object.class)
                .collectList()
                .map(list -> (List<Object>) list);
    }

    public Mono<Void> cancel(OneirosClient client) {
        String sql = buildSql(false);
        return client.query(sql, Object.class).then();
    }

    public String toSql() {
        return buildSql(true);
    }


    private String buildSql(boolean commit) {
        StringBuilder sql = new StringBuilder("BEGIN TRANSACTION;\n");

        for (String statement : statements) {
            sql.append(statement);
            if (!statement.trim().endsWith(";")) {
                sql.append(";");
            }
            sql.append("\n");
        }

        if (returnStatement != null) {
            sql.append(returnStatement).append("\n");
        }

        if (commit && !willCancel) {
            sql.append("COMMIT TRANSACTION;");
        } else {
            sql.append("CANCEL TRANSACTION;");
        }

        return sql.toString();
    }

    // --- Nested Builders ---

    public static class CreateStatementBuilder {
        protected final TransactionBuilder parent;
        protected final String target;
        protected final List<String> setFields = new ArrayList<>();

        CreateStatementBuilder(TransactionBuilder parent, String target) {
            this.parent = parent;
            this.target = target;
        }

        public CreateStatementBuilder set(String field, Object value) {
            setFields.add(field + " = " + formatValue(value));
            return this;
        }

        public CreateStatementBuilder set(String expression) {
            setFields.add(expression);
            return this;
        }

        public TransactionBuilder end() {
            StringBuilder sql = new StringBuilder("CREATE ");
            sql.append(target);
            if (!setFields.isEmpty()) {
                sql.append(" SET ");
                sql.append(String.join(", ", setFields));
            }
            parent.addRawStatement(sql.toString());
            return parent;
        }

        // Chain shortcuts
        public CreateStatementBuilder create(String target) {
            end();
            return parent.create(target);
        }

        public UpdateStatementBuilder update(String target) {
            end();
            return parent.update(target);
        }

        public IfConditionBuilder ifCondition() {
            end();
            return parent.ifCondition();
        }

        public TransactionBuilder returnValue(String expression) {
            end();
            return parent.returnValue(expression);
        }

        public Mono<List<Object>> commit(OneirosClient client) {
            end();
            return parent.commit(client);
        }

        private String formatValue(Object val) {
            if (val instanceof Number) return val.toString();
            if (val instanceof Boolean) return val.toString();
            return "'" + val.toString().replace("'", "\\'") + "'";
        }
    }

    public static class UpdateStatementBuilder {
        private final TransactionBuilder parent;
        private final String target;
        private final List<String> setFields = new ArrayList<>();
        private String whereClause;

        UpdateStatementBuilder(TransactionBuilder parent, String target) {
            this.parent = parent;
            this.target = target;
        }

        public UpdateStatementBuilder set(String field, Object value) {
            setFields.add(field + " = " + formatValue(value));
            return this;
        }

        public UpdateStatementBuilder set(String expression) {
            setFields.add(expression);
            return this;
        }

        public UpdateStatementBuilder where(String condition) {
            this.whereClause = condition;
            return this;
        }

        public TransactionBuilder end() {
            StringBuilder sql = new StringBuilder("UPDATE ");
            sql.append(target);
            if (!setFields.isEmpty()) {
                sql.append(" SET ");
                sql.append(String.join(", ", setFields));
            }
            if (whereClause != null) {
                sql.append(" WHERE ").append(whereClause);
            }
            parent.addRawStatement(sql.toString());
            return parent;
        }

        // Chain shortcuts
        public UpdateStatementBuilder update(String target) {
            end();
            return parent.update(target);
        }

        public CreateStatementBuilder create(String target) {
            end();
            return parent.create(target);
        }

        public IfConditionBuilder ifCondition() {
            end();
            return parent.ifCondition();
        }

        public TransactionBuilder returnValue(String expression) {
            end();
            return parent.returnValue(expression);
        }

        public Mono<List<Object>> commit(OneirosClient client) {
            end();
            return parent.commit(client);
        }

        private String formatValue(Object val) {
            if (val instanceof Number) return val.toString();
            if (val instanceof Boolean) return val.toString();
            return "'" + val.toString().replace("'", "\\'") + "'";
        }
    }

    public static class DeleteStatementBuilder {
        private final TransactionBuilder parent;
        private final String target;
        private String whereClause;

        DeleteStatementBuilder(TransactionBuilder parent, String target) {
            this.parent = parent;
            this.target = target;
        }

        public DeleteStatementBuilder where(String condition) {
            this.whereClause = condition;
            return this;
        }

        public TransactionBuilder end() {
            StringBuilder sql = new StringBuilder("DELETE ");
            sql.append(target);
            if (whereClause != null) {
                sql.append(" WHERE ").append(whereClause);
            }
            parent.addRawStatement(sql.toString());
            return parent;
        }

        public DeleteStatementBuilder delete(String target) {
            end();
            return parent.delete(target);
        }

        public UpdateStatementBuilder update(String target) {
            end();
            return parent.update(target);
        }

        public Mono<List<Object>> commit(OneirosClient client) {
            end();
            return parent.commit(client);
        }
    }

    public static class SelectStatementBuilder {
        private final TransactionBuilder parent;
        private final String[] fields;
        private String fromClause;
        private String whereClause;
        private final List<String> omitFields = new ArrayList<>();
        private final List<String> fetchFields = new ArrayList<>();
        private Integer limit;
        private Duration timeout;

        SelectStatementBuilder(TransactionBuilder parent, String[] fields) {
            this.parent = parent;
            this.fields = fields;
        }

        public SelectStatementBuilder from(String table) {
            this.fromClause = table;
            return this;
        }

        public SelectStatementBuilder where(String condition) {
            this.whereClause = condition;
            return this;
        }

        public SelectStatementBuilder omit(String... fields) {
            omitFields.addAll(Arrays.asList(fields));
            return this;
        }

        public SelectStatementBuilder fetch(String... fields) {
            fetchFields.addAll(Arrays.asList(fields));
            return this;
        }

        public SelectStatementBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public SelectStatementBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public TransactionBuilder end() {
            StringBuilder sql = new StringBuilder("SELECT ");
            if (fields.length == 0) {
                sql.append("*");
            } else {
                sql.append(String.join(", ", fields));
            }

            if (!omitFields.isEmpty()) {
                sql.append(" OMIT ").append(String.join(", ", omitFields));
            }

            if (fromClause != null) {
                sql.append(" FROM ").append(fromClause);
            }

            if (whereClause != null) {
                sql.append(" WHERE ").append(whereClause);
            }

            if (limit != null) {
                sql.append(" LIMIT ").append(limit);
            }

            if (!fetchFields.isEmpty()) {
                sql.append(" FETCH ").append(String.join(", ", fetchFields));
            }

            if (timeout != null) {
                long seconds = timeout.getSeconds();
                sql.append(" TIMEOUT ").append(seconds).append("s");
            }

            parent.addRawStatement(sql.toString());
            return parent;
        }

        public TransactionBuilder returnValue(String expression) {
            end();
            return parent.returnValue(expression);
        }

        public Mono<List<Object>> commit(OneirosClient client) {
            end();
            return parent.commit(client);
        }
    }
}
