package io.oneiros.statement.statements;

import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * LET statement for variable assignment.
 *
 * <p><b>Example:</b>
 * <pre>
 * LetStatement.variable("user_id", "user:john")
 *     .execute(client);
 * </pre>
 *
 * @since 1.0.0
 */
public class LetStatement implements Statement<Object> {

    private final String name;
    private final String expression;

    private LetStatement(String name, String expression) {
        this.name = name;
        this.expression = expression;
    }

    /**
     * Create a LET statement.
     *
     * @param name       the variable name (without $)
     * @param expression the expression or value
     * @return a new LET statement
     */
    public static LetStatement variable(String name, String expression) {
        return new LetStatement(name, expression);
    }

    @Override
    public String toSql() {
        return "LET $" + name + " = " + expression;
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
