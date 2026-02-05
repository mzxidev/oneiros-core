package io.oneiros.statement.statements;

import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * THROW statement for error handling.
 *
 * <p><b>Example:</b>
 * <pre>
 * ThrowStatement.error("Insufficient funds")
 *     .execute(client);
 * </pre>
 *
 * @since 1.0.0
 */
public class ThrowStatement implements Statement<Object> {

    private final String message;

    private ThrowStatement(String message) {
        this.message = message;
    }

    /**
     * Create a THROW statement.
     *
     * @param message the error message
     * @return a new THROW statement
     */
    public static ThrowStatement error(String message) {
        return new ThrowStatement(message);
    }

    @Override
    public String toSql() {
        return "THROW \"" + message.replace("\"", "\\\"") + "\"";
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
