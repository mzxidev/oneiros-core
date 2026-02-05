package io.oneiros.statement.clause;

/**
 * LIMIT clause for limiting result count.
 *
 * Example:
 * LIMIT 10
 * LIMIT 10 START 20
 */
public class LimitClause implements Clause {

    private final int limit;
    private Integer start;

    public LimitClause(int limit) {
        this.limit = limit;
    }

    public LimitClause start(int start) {
        this.start = start;
        return this;
    }

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder(" LIMIT ").append(limit);
        if (start != null) {
            sql.append(" START ").append(start);
        }
        return sql.toString();
    }
}
