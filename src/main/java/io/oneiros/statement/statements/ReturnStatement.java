package io.oneiros.statement.statements;

import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RETURN statement for setting return values.
 *
 * <p><b>Example:</b>
 * <pre>
 * ReturnStatement.value("{ success: true }").execute(client);
 * </pre>
 *
 * @since 1.0.0
 */
public class ReturnStatement implements Statement<Object> {

    private final String expression;

    private ReturnStatement(String expression) {
        this.expression = expression;
    }

    /**
     * Create a RETURN statement.
     *
     * @param expression the expression to return
     * @return a new RETURN statement
     */
    public static ReturnStatement value(String expression) {
        return new ReturnStatement(expression);
    }

    @Override
    public String toSql() {
        return "RETURN " + expression;
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
