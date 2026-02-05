package io.oneiros.statement.clause;

/**
 * EXPLAIN clause for showing query execution plan.
 *
 * Example:
 * EXPLAIN
 * EXPLAIN FULL
 */
public class ExplainClause implements Clause {

    private final boolean full;

    public ExplainClause() {
        this(false);
    }

    public ExplainClause(boolean full) {
        this.full = full;
    }

    @Override
    public String toSql() {
        return full ? " EXPLAIN FULL" : " EXPLAIN";
    }
}
