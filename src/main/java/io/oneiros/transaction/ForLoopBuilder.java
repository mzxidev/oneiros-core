package io.oneiros.transaction;

/**
 * Builder for FOR loops in transactions.
 *
 * <p>Supports iterating over arrays and ranges with full statement support inside the loop body.
 *
 * <p><b>Example usage:</b>
 * <pre>
 * TransactionBuilder.begin()
 *     .forEach("$name", "['Alice', 'Bob', 'Charlie']")
 *         .create("type::record('person', $name)")
 *             .set("name", "$name")
 *         .endCreate()
 *     .endFor()
 *     .commit(client);
 * </pre>
 *
 * <p><b>SurrealQL Output:</b>
 * <pre>
 * FOR $name IN ['Alice', 'Bob', 'Charlie'] {
 *     CREATE type::record('person', $name) SET name = $name;
 * };
 * </pre>
 *
 * @since 1.0.0
 */
public class ForLoopBuilder {

    private final TransactionBuilder parent;
    private final String variable;
    private final String iterable;
    private final StringBuilder loopBody = new StringBuilder();

    /**
     * Creates a new FOR loop builder.
     *
     * @param parent    the parent transaction builder
     * @param variable  the loop variable name (e.g. "$person")
     * @param iterable  the array or range to iterate over
     */
    public ForLoopBuilder(TransactionBuilder parent, String variable, String iterable) {
        this.parent = parent;
        this.variable = variable;
        this.iterable = iterable;
    }

    // --- Nested Statement Builders ---

    /**
     * Create a CREATE statement inside the FOR loop.
     *
     * @param target the target table or record
     * @return a CREATE statement builder for the loop
     */
    public CreateInForBuilder create(String target) {
        return new CreateInForBuilder(this, target);
    }

    /**
     * Create an UPDATE statement inside the FOR loop.
     *
     * @param target the target to update
     * @return an UPDATE statement builder for the loop
     */
    public UpdateInForBuilder update(String target) {
        return new UpdateInForBuilder(this, target);
    }

    /**
     * Create a DELETE statement inside the FOR loop.
     *
     * @param target the target to delete
     * @return a DELETE statement builder for the loop
     */
    public DeleteInForBuilder delete(String target) {
        return new DeleteInForBuilder(this, target);
    }

    /**
     * Add an IF condition inside the FOR loop.
     *
     * @return an IF condition builder
     */
    public IfInForBuilder ifCondition() {
        return new IfInForBuilder(this);
    }

    /**
     * Add a CONTINUE statement to skip to the next iteration.
     *
     * @return this builder for chaining
     */
    public ForLoopBuilder continueLoop() {
        loopBody.append("CONTINUE;");
        return this;
    }

    /**
     * Add a BREAK statement to exit the loop early.
     *
     * @return this builder for chaining
     */
    public ForLoopBuilder breakLoop() {
        loopBody.append("BREAK;");
        return this;
    }

    /**
     * Add a raw statement to the loop body.
     *
     * @param statement the SurrealQL statement
     * @return this builder for chaining
     */
    public ForLoopBuilder addStatement(String statement) {
        loopBody.append(statement);
        if (!statement.trim().endsWith(";")) {
            loopBody.append(";");
        }
        return this;
    }

    /**
     * Complete the FOR loop and return to the parent builder.
     *
     * @return the parent transaction builder
     */
    public TransactionBuilder endFor() {
        StringBuilder sql = new StringBuilder("FOR ");
        sql.append(variable);
        sql.append(" IN ");
        sql.append(iterable);
        sql.append(" { ");
        sql.append(loopBody);
        sql.append(" };");

        parent.addRawStatement(sql.toString());
        return parent;
    }

    /**
     * Append a statement to the loop body (internal use).
     *
     * @param statement the statement to append
     */
    void appendToBody(String statement) {
        loopBody.append(statement);
        if (!statement.trim().endsWith(";")) {
            loopBody.append(";");
        }
    }

    // --- Nested Builder Classes ---

    /**
     * CREATE statement builder for use inside a FOR loop.
     */
    public static class CreateInForBuilder {
        private final ForLoopBuilder forBuilder;
        private final String target;
        private final StringBuilder setClause = new StringBuilder();

        CreateInForBuilder(ForLoopBuilder forBuilder, String target) {
            this.forBuilder = forBuilder;
            this.target = target;
        }

        public CreateInForBuilder set(String field, Object value) {
            if (setClause.length() > 0) {
                setClause.append(", ");
            }
            setClause.append(field).append(" = ");
            if (value instanceof Number || value instanceof Boolean) {
                setClause.append(value);
            } else {
                setClause.append("'").append(value.toString().replace("'", "\\'")).append("'");
            }
            return this;
        }

        public CreateInForBuilder set(String expression) {
            if (setClause.length() > 0) {
                setClause.append(", ");
            }
            setClause.append(expression);
            return this;
        }

        public ForLoopBuilder endCreate() {
            StringBuilder sql = new StringBuilder("CREATE ");
            sql.append(target);
            if (setClause.length() > 0) {
                sql.append(" SET ").append(setClause);
            }
            forBuilder.appendToBody(sql.toString());
            return forBuilder;
        }
    }

    /**
     * UPDATE statement builder for use inside a FOR loop.
     */
    public static class UpdateInForBuilder {
        private final ForLoopBuilder forBuilder;
        private final String target;
        private final StringBuilder setClause = new StringBuilder();
        private String whereClause;

        UpdateInForBuilder(ForLoopBuilder forBuilder, String target) {
            this.forBuilder = forBuilder;
            this.target = target;
        }

        public UpdateInForBuilder set(String field, Object value) {
            if (setClause.length() > 0) {
                setClause.append(", ");
            }
            setClause.append(field).append(" = ");
            if (value instanceof Number || value instanceof Boolean) {
                setClause.append(value);
            } else {
                setClause.append("'").append(value.toString().replace("'", "\\'")).append("'");
            }
            return this;
        }

        public UpdateInForBuilder set(String expression) {
            if (setClause.length() > 0) {
                setClause.append(", ");
            }
            setClause.append(expression);
            return this;
        }

        public UpdateInForBuilder where(String condition) {
            this.whereClause = condition;
            return this;
        }

        public ForLoopBuilder endUpdate() {
            StringBuilder sql = new StringBuilder("UPDATE ");
            sql.append(target);
            if (setClause.length() > 0) {
                sql.append(" SET ").append(setClause);
            }
            if (whereClause != null) {
                sql.append(" WHERE ").append(whereClause);
            }
            forBuilder.appendToBody(sql.toString());
            return forBuilder;
        }
    }

    /**
     * DELETE statement builder for use inside a FOR loop.
     */
    public static class DeleteInForBuilder {
        private final ForLoopBuilder forBuilder;
        private final String target;
        private String whereClause;

        DeleteInForBuilder(ForLoopBuilder forBuilder, String target) {
            this.forBuilder = forBuilder;
            this.target = target;
        }

        public DeleteInForBuilder where(String condition) {
            this.whereClause = condition;
            return this;
        }

        public ForLoopBuilder endDelete() {
            StringBuilder sql = new StringBuilder("DELETE ");
            sql.append(target);
            if (whereClause != null) {
                sql.append(" WHERE ").append(whereClause);
            }
            forBuilder.appendToBody(sql.toString());
            return forBuilder;
        }
    }

    /**
     * IF condition builder for use inside a FOR loop.
     */
    public static class IfInForBuilder {
        private final ForLoopBuilder forBuilder;
        private final StringBuilder condition = new StringBuilder();
        private final StringBuilder thenBlock = new StringBuilder();

        IfInForBuilder(ForLoopBuilder forBuilder) {
            this.forBuilder = forBuilder;
        }

        public IfInForBuilder field(String fieldName) {
            if (!condition.isEmpty() && !condition.toString().endsWith("!")) {
                condition.append(" ");
            }
            condition.append(fieldName);
            return this;
        }

        public IfInForBuilder eq(Object value) {
            condition.append(" = ");
            if (value instanceof Number || value instanceof Boolean) {
                condition.append(value);
            } else {
                condition.append("'").append(value.toString().replace("'", "\\'")).append("'");
            }
            return this;
        }

        public IfInForBuilder lt(Object value) {
            condition.append(" < ");
            if (value instanceof Number) {
                condition.append(value);
            } else {
                condition.append("'").append(value.toString().replace("'", "\\'")).append("'");
            }
            return this;
        }

        public IfInForBuilder gt(Object value) {
            condition.append(" > ");
            if (value instanceof Number) {
                condition.append(value);
            } else {
                condition.append("'").append(value.toString().replace("'", "\\'")).append("'");
            }
            return this;
        }

        public IfInForBuilder then() {
            return this;
        }

        public IfInForBuilder continueLoop() {
            thenBlock.append("CONTINUE;");
            return this;
        }

        public IfInForBuilder breakLoop() {
            thenBlock.append("BREAK;");
            return this;
        }

        public ForLoopBuilder endIf() {
            StringBuilder sql = new StringBuilder("IF ");
            sql.append(condition);
            sql.append(" { ");
            sql.append(thenBlock);
            sql.append(" }");
            forBuilder.appendToBody(sql.toString());
            return forBuilder;
        }
    }
}
