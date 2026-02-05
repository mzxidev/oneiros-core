package io.oneiros.statement.clause;

import java.util.ArrayList;
import java.util.List;

/**
 * OMIT clause for excluding fields from results.
 *
 * Example:
 * OMIT password, secretKey
 */
public class OmitClause implements Clause {

    private final List<String> fields = new ArrayList<>();

    public OmitClause add(String field) {
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
        return " OMIT " + String.join(", ", fields);
    }
}
