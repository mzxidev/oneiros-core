package io.oneiros.statement.statements;

import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SLEEP statement for pausing execution.
 *
 * <p><b>Example:</b>
 * <pre>
 * SleepStatement.duration("100ms").execute(client);
 * SleepStatement.duration("5s").execute(client);
 * </pre>
 *
 * @since 1.0.0
 */
public class SleepStatement implements Statement<Object> {

    private final String duration;

    private SleepStatement(String duration) {
        this.duration = duration;
    }

    /**
     * Create a SLEEP statement.
     *
     * @param duration the sleep duration (e.g. "100ms", "5s")
     * @return a new SLEEP statement
     */
    public static SleepStatement duration(String duration) {
        return new SleepStatement(duration);
    }

    @Override
    public String toSql() {
        return "SLEEP " + duration;
    }

    @Override
    public Flux<Object> execute(OneirosClient client) {
        return client.query(toSql(), Object.class);
    }

    @Override
    public Mono<Object> executeOne(OneirosClient client) {
        return execute(client).next();
    }
}
