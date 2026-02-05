package io.oneiros.statement.clause;

import java.util.ArrayList;
import java.util.List;

/**
 * FETCH clause for loading related records.
 *
 * Example:
 * FETCH profile, permissions
 */
public class FetchClause implements Clause {

    private final List<String> fields = new ArrayList<>();

    public FetchClause add(String field) {
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
        return " FETCH " + String.join(", ", fields);
    }
}
