package io.oneiros.statement.clause;

import java.time.Duration;

/**
 * TIMEOUT clause for setting query timeout.
 *
 * Example:
 * TIMEOUT 5s
 * TIMEOUT 500ms
 */
public class TimeoutClause implements Clause {

    private final Duration duration;

    public TimeoutClause(Duration duration) {
        this.duration = duration;
    }

    @Override
    public String toSql() {
        long seconds = duration.getSeconds();
        long millis = duration.toMillis() % 1000;

        StringBuilder sql = new StringBuilder(" TIMEOUT ");
        if (millis > 0) {
            sql.append(seconds).append("s").append(millis).append("ms");
        } else {
            sql.append(seconds).append("s");
        }
        return sql.toString();
    }
}
