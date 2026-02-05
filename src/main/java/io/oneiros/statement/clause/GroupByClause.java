package io.oneiros.statement.clause;

import java.util.ArrayList;
import java.util.List;

/**
 * GROUP BY clause for grouping results.
 *
 * Example:
 * GROUP BY country, city
 */
public class GroupByClause implements Clause {

    private final List<String> fields = new ArrayList<>();

    public GroupByClause add(String field) {
        fields.add(field);
        return this;
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    @Override
    public String toSql() {
        if (fields.isEmpty()) {
            return "";
        }
        return " GROUP BY " + String.join(", ", fields);
    }
}
