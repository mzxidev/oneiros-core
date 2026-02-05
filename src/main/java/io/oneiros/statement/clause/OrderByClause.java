package io.oneiros.statement.clause;

/**
 * ORDER BY clause for sorting results.
 *
 * Example:
 * ORDER BY name ASC, age DESC
 */
public class OrderByClause implements Clause {

    private String field;
    private String direction = "ASC";

    public OrderByClause(String field) {
        this.field = field;
    }

    public OrderByClause(String field, String direction) {
        this.field = field;
        this.direction = direction;
    }

    public OrderByClause asc() {
        this.direction = "ASC";
        return this;
    }

    public OrderByClause desc() {
        this.direction = "DESC";
        return this;
    }

    @Override
    public String toSql() {
        return " ORDER BY " + field + " " + direction;
    }
}
