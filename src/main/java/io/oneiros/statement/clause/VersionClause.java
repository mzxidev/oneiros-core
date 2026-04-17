package io.oneiros.statement.clause;

import java.time.Instant;

/**
 * VERSION clause for SurrealDB Time-Travel queries.
 */
public class VersionClause implements Clause {

    private final Instant timestamp;

    public VersionClause(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toSql() {
        if (timestamp == null) {
            return "";
        }
        return " VERSION d'" + timestamp.toString() + "'";
    }
}
