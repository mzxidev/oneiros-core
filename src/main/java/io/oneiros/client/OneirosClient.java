package io.oneiros.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * OneirosClient provides a reactive interface to SurrealDB using the RPC protocol.
 * All methods follow the official SurrealDB RPC specification.
 *
 * @see <a href="https://surrealdb.com/docs/surrealdb/integration/rpc">SurrealDB RPC Protocol</a>
 */
public interface OneirosClient {

    // ============================================================
    // CONNECTION & SESSION MANAGEMENT
    // ============================================================

    /**
     * Establishes connection to SurrealDB and performs signin + use namespace/database.
     * @return Mono signaling completion
     */
    Mono<Void> connect();

    /**
     * Checks if the client is currently connected to SurrealDB.
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Closes the connection to SurrealDB.
     * @return Mono signaling completion
     */
    Mono<Void> disconnect();

    /**
     * Authenticates using a JWT token.
     * RPC: authenticate [token]
     *
     * @param token JWT authentication token
     * @return Mono<Void> signaling completion
     */
    Mono<Void> authenticate(String token);

    /**
     * Signs in as root, namespace, database, or record user.
     * RPC: signin [credentials]
     *
     * @param credentials Authentication credentials (user, pass, NS, DB, AC, etc.)
     * @return Mono<String> containing the JWT token (may be null for root users)
     */
    Mono<String> signin(Map<String, Object> credentials);

    /**
     * Signs up a user using a record access method.
     * RPC: signup [credentials]
     *
     * @param credentials Signup credentials (NS, DB, AC, username, password, etc.)
     * @return Mono<String> containing the JWT token
     */
    Mono<String> signup(Map<String, Object> credentials);

    /**
     * Invalidates the current session.
     * RPC: invalidate
     *
     * @return Mono<Void> signaling completion
     */
    Mono<Void> invalidate();

    /**
     * Returns info about the authenticated record user.
     * RPC: info
     *
     * @param resultType Class to deserialize the user info into
     * @return Mono<T> containing user information
     */
    <T> Mono<T> info(Class<T> resultType);

    /**
     * Resets all session state (auth, namespace, database, variables, live queries).
     * RPC: reset
     *
     * @return Mono<Void> signaling completion
     */
    Mono<Void> reset();

    /**
     * Sends a ping to keep the connection alive.
     * RPC: ping
     *
     * @return Mono<Void> signaling completion
     */
    Mono<Void> ping();

    /**
     * Returns version information about SurrealDB.
     * RPC: version
     *
     * @return Mono<Map> containing version, build, and timestamp
     */
    Mono<Map<String, Object>> version();

    // ============================================================
    // NAMESPACE & DATABASE SELECTION
    // ============================================================

    /**
     * Sets the namespace and/or database for subsequent queries.
     * RPC: use [ns, db]
     *
     * @param namespace Namespace to use (null to unset, "none" to keep current)
     * @param database Database to use (null to unset, "none" to keep current)
     * @return Mono<Void> signaling completion
     */
    Mono<Void> use(String namespace, String database);

    // ============================================================
    // VARIABLES
    // ============================================================

    /**
     * Defines a session variable.
     * RPC: let [name, value]
     *
     * @param name Variable name (without $ prefix)
     * @param value Variable value
     * @return Mono<Void> signaling completion
     */
    Mono<Void> let(String name, Object value);

    /**
     * Removes a session variable.
     * RPC: unset [name]
     *
     * @param name Variable name (without $ prefix)
     * @return Mono<Void> signaling completion
     */
    Mono<Void> unset(String name);

    // ============================================================
    // QUERIES
    // ============================================================

    /**
     * Executes a SurrealQL query.
     * RPC: query [sql, vars]
     *
     * @param sql SurrealQL query string
     * @param resultType Class to deserialize results into
     * @return Flux<T> of query results
     */
    <T> Flux<T> query(String sql, Class<T> resultType);

    /**
     * Executes a SurrealQL query with parameters.
     * RPC: query [sql, vars]
     *
     * @param sql SurrealQL query string (use $param for variables)
     * @param params Query parameters
     * @param resultType Class to deserialize results into
     * @return Flux<T> of query results
     */
    <T> Flux<T> query(String sql, Map<String, Object> params, Class<T> resultType);

    /**
     * Executes a GraphQL query.
     * RPC: graphql [query, options]
     *
     * @param query GraphQL query string or object with query/variables/operationName
     * @param options Optional settings (pretty, format)
     * @return Mono<Map> containing GraphQL response
     */
    Mono<Map<String, Object>> graphql(Object query, Map<String, Object> options);

    /**
     * Executes a function, custom function, or ML model.
     * RPC: run [func_name, version, args]
     *
     * @param functionName Function name (fn::custom or ml::model)
     * @param version Version (required for ML models, optional otherwise)
     * @param args Function arguments
     * @param resultType Class to deserialize result into
     * @return Mono<T> containing function result
     */
    <T> Mono<T> run(String functionName, String version, List<Object> args, Class<T> resultType);

    // ============================================================
    // CRUD OPERATIONS
    // ============================================================

    /**
     * Selects all records from a table or a specific record.
     * RPC: select [thing]
     *
     * @param thing Table name or Record ID
     * @param resultType Class to deserialize results into
     * @return Flux<T> of selected records
     */
    <T> Flux<T> select(String thing, Class<T> resultType);

    /**
     * Selects records with additional options (WHERE, LIMIT, etc.).
     * RPC v2: select [thing, options]
     *
     * @param thing Table name or Record ID
     * @param options Query options (where, limit, start, etc.)
     * @param resultType Class to deserialize results into
     * @return Flux<T> of selected records
     */
    <T> Flux<T> select(String thing, Map<String, Object> options, Class<T> resultType);

    /**
     * Creates a record with random or specified ID.
     * RPC: create [thing, data]
     *
     * @param thing Table name or Record ID
     * @param data Record data
     * @param resultType Class to deserialize result into
     * @return Mono<T> containing created record
     */
    <T> Mono<T> create(String thing, Object data, Class<T> resultType);

    /**
     * Inserts one or multiple records.
     * RPC: insert [thing, data]
     *
     * @param thing Table name
     * @param data Single record or array of records
     * @param resultType Class to deserialize results into
     * @return Flux<T> of inserted records
     */
    <T> Flux<T> insert(String thing, Object data, Class<T> resultType);

    /**
     * Updates all records in a table or a specific record.
     * RPC: update [thing, data]
     *
     * @param thing Table name or Record ID
     * @param data Update data
     * @param resultType Class to deserialize results into
     * @return Flux<T> of updated records
     */
    <T> Flux<T> update(String thing, Object data, Class<T> resultType);

    /**
     * Updates or inserts records (UPSERT).
     * RPC: upsert [thing, data]
     *
     * @param thing Table name or Record ID
     * @param data Upsert data
     * @param resultType Class to deserialize results into
     * @return Flux<T> of upserted records
     */
    <T> Flux<T> upsert(String thing, Object data, Class<T> resultType);

    /**
     * Merges data into existing records (partial update).
     * RPC: merge [thing, data]
     *
     * @param thing Table name or Record ID
     * @param data Data to merge
     * @param resultType Class to deserialize results into
     * @return Flux<T> of merged records
     */
    <T> Flux<T> merge(String thing, Object data, Class<T> resultType);

    /**
     * Applies JSON patches to records.
     * RPC: patch [thing, patches, diff]
     *
     * @param thing Table name or Record ID
     * @param patches Array of JSON Patch operations
     * @param returnDiff If true, returns diff instead of full record
     * @return Flux<Map> of patched records or diffs
     */
    Flux<Map<String, Object>> patch(String thing, List<Map<String, Object>> patches, boolean returnDiff);

    /**
     * Deletes all records in a table or a specific record.
     * RPC: delete [thing]
     *
     * @param thing Table name or Record ID
     * @param resultType Class to deserialize deleted records into
     * @return Flux<T> of deleted records
     */
    <T> Flux<T> delete(String thing, Class<T> resultType);

    // ============================================================
    // GRAPH RELATIONS
    // ============================================================

    /**
     * Creates a graph relation between two records.
     * RPC: relate [in, relation, out, data]
     *
     * @param in Source record ID
     * @param relation Relation table name
     * @param out Target record ID
     * @param data Optional relation data
     * @param resultType Class to deserialize result into
     * @return Mono<T> containing created relation
     */
    <T> Mono<T> relate(String in, String relation, String out, Object data, Class<T> resultType);

    /**
     * Inserts a relation record.
     * RPC: insert_relation [table, data]
     *
     * @param table Relation table name (null to infer from data.id)
     * @param data Relation data (must include 'in' and 'out')
     * @param resultType Class to deserialize result into
     * @return Mono<T> containing inserted relation
     */
    <T> Mono<T> insertRelation(String table, Object data, Class<T> resultType);

    // ============================================================
    // LIVE QUERIES
    // ============================================================

    /**
     * Starts a live query on a table.
     * RPC: live [table, diff]
     *
     * @param table Table name to watch
     * @param diff If true, returns diffs instead of full records
     * @return Mono<String> containing live query UUID
     */
    Mono<String> live(String table, boolean diff);

    /**
     * Listens to real-time notifications from a live query.
     *
     * @param liveQueryId Live query UUID
     * @return Flux<Map> of notification events
     */
    Flux<Map<String, Object>> listenToLiveQuery(String liveQueryId);

    /**
     * Kills an active live query.
     * RPC: kill [queryUuid]
     *
     * @param liveQueryId Live query UUID to kill
     * @return Mono<Void> signaling completion
     */
    Mono<Void> kill(String liveQueryId);
}



