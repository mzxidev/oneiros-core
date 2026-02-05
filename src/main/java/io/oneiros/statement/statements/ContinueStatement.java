package io.oneiros.statement.statements;

import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * CONTINUE statement for skipping loop iterations.
 *
 * <p>Can only be used inside FOR loops.
 *
 * <p><b>Example:</b>
 * <pre>
 * ContinueStatement.skip().execute(client);
 * </pre>
 *
 * @since 1.0.0
 */
public class ContinueStatement implements Statement<Object> {

    private ContinueStatement() {}

    /**
     * Create a CONTINUE statement.
     *
     * @return a new CONTINUE statement
     */
    public static ContinueStatement skip() {
        return new ContinueStatement();
    }

    @Override
    public String toSql() {
        return "CONTINUE";
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
