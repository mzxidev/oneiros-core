package io.oneiros.statement.clause;

import java.util.ArrayList;
import java.util.List;

/**
 * SPLIT clause for splitting results into subqueries.
 *
 * Example:
 * SPLIT category
 */
public class SplitClause implements Clause {

    private final List<String> fields = new ArrayList<>();

    public SplitClause add(String field) {
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
        return " SPLIT " + String.join(", ", fields);
    }
}
