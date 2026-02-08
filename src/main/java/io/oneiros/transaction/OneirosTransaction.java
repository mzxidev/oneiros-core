package io.oneiros.transaction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Represents an active ACID transaction on SurrealDB.
 *
 * <p>All operations within a transaction:
 * <ul>
 *   <li>Are executed sequentially on the same WebSocket connection</li>
 *   <li>Are automatically rolled back on error (CANCEL TRANSACTION)</li>
 *   <li>Are automatically committed on successful completion (COMMIT TRANSACTION)</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * client.transaction(tx -> {
 *     return tx.query("UPDATE account:sender SET balance -= 100", Map.class)
 *              .thenMany(tx.query("UPDATE account:receiver SET balance += 100", Map.class))
 *              .then(tx.query("CREATE transaction SET amount = 100", Map.class));
 * }).subscribe(
 *     result -> log.info("Transaction committed: {}", result),
 *     error -> log.error("Transaction rolled back: {}", error.getMessage())
 * );
 * }</pre>
 *
 * <p><strong>Isolation Level:</strong> SurrealDB uses optimistic concurrency control.
 * If concurrent modifications conflict, the transaction will fail and must be retried.
 *
 * @see <a href="https://surrealdb.com/docs/surrealdb/surrealql/transactions">SurrealDB Transactions</a>
 */
public interface OneirosTransaction {

    // ============================================================
    // QUERIES
    // ============================================================

    /**
     * Executes a SurrealQL query within this transaction.
     *
     * @param sql SurrealQL query string
     * @param resultType Class to deserialize results into
     * @param <T> Result type
     * @return Flux of query results
     */
    <T> Flux<T> query(String sql, Class<T> resultType);

    /**
     * Executes a SurrealQL query with parameters within this transaction.
     *
     * @param sql SurrealQL query string (use $param for variables)
     * @param params Query parameters
     * @param resultType Class to deserialize results into
     * @param <T> Result type
     * @return Flux of query results
     */
    <T> Flux<T> query(String sql, Map<String, Object> params, Class<T> resultType);

    // ============================================================
    // CRUD OPERATIONS
    // ============================================================

    /**
     * Selects records within this transaction.
     *
     * @param thing Table name or Record ID
     * @param resultType Class to deserialize results into
     * @param <T> Result type
     * @return Flux of selected records
     */
    <T> Flux<T> select(String thing, Class<T> resultType);

    /**
     * Creates a record within this transaction.
     *
     * @param thing Table name or Record ID
     * @param data Record data
     * @param resultType Class to deserialize result into
     * @param <T> Result type
     * @return Mono of created record
     */
    <T> Mono<T> create(String thing, Object data, Class<T> resultType);

    /**
     * Inserts records within this transaction.
     *
     * @param thing Table name
     * @param data Single record or array of records
     * @param resultType Class to deserialize results into
     * @param <T> Result type
     * @return Flux of inserted records
     */
    <T> Flux<T> insert(String thing, Object data, Class<T> resultType);

    /**
     * Updates records within this transaction.
     *
     * @param thing Table name or Record ID
     * @param data Update data
     * @param resultType Class to deserialize results into
     * @param <T> Result type
     * @return Flux of updated records
     */
    <T> Flux<T> update(String thing, Object data, Class<T> resultType);

    /**
     * Updates or inserts records (UPSERT) within this transaction.
     *
     * @param thing Table name or Record ID
     * @param data Upsert data
     * @param resultType Class to deserialize results into
     * @param <T> Result type
     * @return Flux of upserted records
     */
    <T> Flux<T> upsert(String thing, Object data, Class<T> resultType);

    /**
     * Merges data into records (partial update) within this transaction.
     *
     * @param thing Table name or Record ID
     * @param data Data to merge
     * @param resultType Class to deserialize results into
     * @param <T> Result type
     * @return Flux of merged records
     */
    <T> Flux<T> merge(String thing, Object data, Class<T> resultType);

    /**
     * Applies JSON patches to records within this transaction.
     *
     * @param thing Table name or Record ID
     * @param patches Array of JSON Patch operations
     * @param returnDiff If true, returns diff instead of full record
     * @return Flux of patched records or diffs
     */
    Flux<Map<String, Object>> patch(String thing, List<Map<String, Object>> patches, boolean returnDiff);

    /**
     * Deletes records within this transaction.
     *
     * @param thing Table name or Record ID
     * @param resultType Class to deserialize deleted records into
     * @param <T> Result type
     * @return Flux of deleted records
     */
    <T> Flux<T> delete(String thing, Class<T> resultType);

    // ============================================================
    // GRAPH RELATIONS
    // ============================================================

    /**
     * Creates a graph relation within this transaction.
     *
     * @param in Source record ID
     * @param relation Relation table name
     * @param out Target record ID
     * @param data Optional relation data
     * @param resultType Class to deserialize result into
     * @param <T> Result type
     * @return Mono of created relation
     */
    <T> Mono<T> relate(String in, String relation, String out, Object data, Class<T> resultType);

    /**
     * Inserts a relation record within this transaction.
     *
     * @param table Relation table name
     * @param data Relation data (must include 'in' and 'out')
     * @param resultType Class to deserialize result into
     * @param <T> Result type
     * @return Mono of inserted relation
     */
    <T> Mono<T> insertRelation(String table, Object data, Class<T> resultType);

    // ============================================================
    // SESSION VARIABLES
    // ============================================================

    /**
     * Defines a session variable within this transaction scope.
     * Note: Session variables are global to the connection, not transaction-scoped.
     *
     * @param name Variable name (without $ prefix)
     * @param value Variable value
     * @return Mono signaling completion
     */
    Mono<Void> let(String name, Object value);

    /**
     * Removes a session variable.
     *
     * @param name Variable name (without $ prefix)
     * @return Mono signaling completion
     */
    Mono<Void> unset(String name);
}

