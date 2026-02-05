package io.oneiros.statement.clause;

import java.util.ArrayList;
import java.util.List;

/**
 * WHERE clause for filtering results.
 *
 * Example:
 * WHERE name = 'Alice' AND age > 18
 */
public class WhereClause implements Clause {

    private final List<String> conditions = new ArrayList<>();

    public WhereClause add(String condition) {
        conditions.add(condition);
        return this;
    }

    public WhereClause and(String condition) {
        conditions.add("AND " + condition);
        return this;
    }

    public WhereClause or(String condition) {
        conditions.add("OR " + condition);
        return this;
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    @Override
    public String toSql() {
        if (conditions.isEmpty()) {
            return "";
        }

        StringBuilder sql = new StringBuilder(" WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            String condition = conditions.get(i);
            if (i > 0 && !condition.startsWith("AND ") && !condition.startsWith("OR ")) {
                sql.append(" AND ");
            }
            sql.append(condition);
        }
        return sql.toString();
    }
}
