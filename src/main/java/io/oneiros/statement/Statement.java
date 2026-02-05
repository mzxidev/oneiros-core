package io.oneiros.statement;

import io.oneiros.client.OneirosClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Base interface for all SurrealQL statements.
 *
 * Statements represent the main operations in SurrealQL:
 * - SELECT, CREATE, UPDATE, DELETE, RELATE
 * - BEGIN/COMMIT, THROW, RETURN
 */
public interface Statement<T> {

    /**
     * Build the SQL string for this statement.
     */
    String toSql();

    /**
     * Execute the statement and return results.
     */
    Flux<T> execute(OneirosClient client);

    /**
     * Execute the statement and return a single result.
     */
    Mono<T> executeOne(OneirosClient client);
}
