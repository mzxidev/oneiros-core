package io.oneiros.statement.clause;

/**
 * PARALLEL clause for enabling parallel execution.
 *
 * Example:
 * PARALLEL
 */
public class ParallelClause implements Clause {

    @Override
    public String toSql() {
        return " PARALLEL";
    }
}
