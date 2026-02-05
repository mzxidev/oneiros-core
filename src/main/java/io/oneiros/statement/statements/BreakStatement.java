package io.oneiros.statement.statements;

import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * BREAK statement for exiting loops early.
 *
 * <p>Can only be used inside FOR loops.
 *
 * <p><b>Example:</b>
 * <pre>
 * BreakStatement.exit().execute(client);
 * </pre>
 *
 * @since 1.0.0
 */
public class BreakStatement implements Statement<Object> {

    private BreakStatement() {}

    /**
     * Create a BREAK statement.
     *
     * @return a new BREAK statement
     */
    public static BreakStatement exit() {
        return new BreakStatement();
    }

    @Override
    public String toSql() {
        return "BREAK";
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
