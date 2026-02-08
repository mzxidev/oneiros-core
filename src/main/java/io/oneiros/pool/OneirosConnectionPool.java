package io.oneiros.pool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.oneiros.client.OneirosClient;
import io.oneiros.client.OneirosWebsocketClient;
import io.oneiros.config.OneirosProperties;
import io.oneiros.core.OneirosConfig;
import io.oneiros.transaction.OneirosTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Connection pool for managing multiple WebSocket connections to SurrealDB.
 * Provides load balancing, health checking, and automatic reconnection.
 *
 * <p>Supports both framework-agnostic {@link OneirosConfig} and Spring-based {@link OneirosProperties}.
 */
public class OneirosConnectionPool implements OneirosClient {

    private static final Logger log = LoggerFactory.getLogger(OneirosConnectionPool.class);

    // Framework-agnostic configuration
    private final OneirosConfig config;

    // Spring-specific (kept for backward compatibility)
    private final OneirosProperties properties;

    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;
    private final int poolSize;

    private final List<PooledConnection> connections = new CopyOnWriteArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final long healthCheckIntervalSeconds;

    private volatile boolean initialized = false;
    private volatile Disposable healthCheckScheduler;

    // Signal when pool is ready to accept queries
    private final Sinks.One<Void> readySink = Sinks.one();

    /**
     * Creates a connection pool from framework-agnostic OneirosConfig.
     */
    public OneirosConnectionPool(
            OneirosConfig config,
            ObjectMapper objectMapper,
            CircuitBreaker circuitBreaker,
            int poolSize
    ) {
        this.config = config;
        this.properties = null;
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreaker;
        this.poolSize = poolSize;
        this.healthCheckIntervalSeconds = config.getPool() != null
            ? config.getPool().getHealthCheckInterval()
            : 30;
    }

    /**
     * Creates a connection pool from Spring OneirosProperties (backward compatibility).
     */
    public OneirosConnectionPool(
            OneirosProperties properties,
            ObjectMapper objectMapper,
            CircuitBreaker circuitBreaker,
            int poolSize
    ) {
        this.properties = properties;
        this.config = OneirosConfig.fromProperties(properties);
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreaker;
        this.poolSize = poolSize;
        this.healthCheckIntervalSeconds = properties.getPool() != null
            ? properties.getPool().getHealthCheckInterval()
            : 30;
    }

    /**
     * Initializes the connection pool by creating all connections.
     * Connections are created sequentially to ensure proper initialization.
     * Each connection completes signin and use before the next one starts.
     * Blocks until at least one connection is successfully established.
     */
    @Override
    public Mono<Void> connect() {
        if (initialized) {
            log.debug("Connection pool already initialized, skipping");
            return Mono.empty();
        }

        log.info("🏊 Initializing connection pool with {} connections", poolSize);

        // Create connections sequentially using concatMap to ensure proper ordering
        return Flux.range(1, poolSize)
            .concatMap(i -> createConnection()
                .doOnSuccess(conn -> {
                    connections.add(conn);
                    log.info("✅ Connection #{}/{} added to pool", i, poolSize);
                })
                .onErrorResume(error -> {
                    log.error("❌ Failed to create connection #{}: {}", i, error.getMessage());
                    // Continue with other connections even if one fails
                    return Mono.empty();
                })
                // Important: Add small delay between connections to prevent race conditions
                .delayElement(Duration.ofMillis(50))
            )
            .then()
            .doOnSuccess(v -> {
                if (connections.isEmpty()) {
                    IllegalStateException error = new IllegalStateException(
                        "Failed to establish any connections to SurrealDB. " +
                        "Please check your configuration and that SurrealDB is running.");
                    readySink.tryEmitError(error);
                    throw error;
                }

                initialized = true;
                log.info("🏊 Connection pool initialized with {}/{} connections ({}% success rate)",
                    connections.size(), poolSize, (connections.size() * 100 / poolSize));

                if (connections.size() < poolSize) {
                    log.warn("⚠️ Pool initialized with fewer connections than requested. " +
                             "Some connections failed to establish.");
                }

                // Start framework-agnostic health check scheduler
                startHealthCheckScheduler();

                // Signal that pool is ready
                readySink.tryEmitValue(null);
            })
            .doOnError(error -> {
                log.error("❌ Pool initialization failed: {}", error.getMessage());
                readySink.tryEmitError(error);
            });
    }

    /**
     * Wait until the pool is ready to accept queries.
     * This is used by clients that need to ensure the pool is initialized before making requests.
     */
    public Mono<Void> waitUntilReady() {
        if (initialized) {
            return Mono.empty();
        }
        return readySink.asMono();
    }

    /**
     * Creates a new pooled connection and ensures it's connected.
     * Sequential execution ensures signin and use complete before next connection.
     */
    private Mono<PooledConnection> createConnection() {
        OneirosWebsocketClient client = new OneirosWebsocketClient(
            config,
            objectMapper,
            circuitBreaker
        );

        return client.connect()
            .then(Mono.defer(() -> {
                if (!client.isConnected()) {
                    return Mono.error(new IllegalStateException("Client failed to establish connection"));
                }
                log.debug("✅ New connection established and verified");
                return Mono.just(new PooledConnection(client));
            }))
            .timeout(Duration.ofSeconds(15))
            .doOnError(e -> {
                log.error("Failed to create connection: {}", e.getMessage());
                if (e.getCause() != null) {
                    log.error("Root cause: {}", e.getCause().getMessage());
                }
            });
    }

    /**
     * Selects the next healthy connection using round-robin load balancing.
     */
    private Mono<PooledConnection> selectConnection() {
        if (connections.isEmpty()) {
            return Mono.error(new IllegalStateException("No connections available in pool"));
        }

        List<PooledConnection> healthyConnections = connections.stream()
            .filter(PooledConnection::isHealthy)
            .toList();

        if (healthyConnections.isEmpty()) {
            log.warn("⚠️ No healthy connections available, attempting recovery");
            return recoverConnection();
        }

        int index = Math.abs(roundRobinIndex.getAndIncrement() % healthyConnections.size());
        PooledConnection selected = healthyConnections.get(index);

        log.debug("🎯 Selected connection #{} (health: {})",
            connections.indexOf(selected) + 1, selected.getStatus());

        return Mono.just(selected);
    }

    /**
     * Attempts to recover an unhealthy connection or create a new one.
     */
    private Mono<PooledConnection> recoverConnection() {
        PooledConnection unhealthy = connections.stream()
            .filter(conn -> conn.getStatus() == PooledConnection.Status.UNHEALTHY)
            .findFirst()
            .orElse(null);

        if (unhealthy != null) {
            log.info("🔄 Attempting to reconnect unhealthy connection");
            return unhealthy.reconnect()
                .thenReturn(unhealthy)
                .onErrorResume(error -> {
                    log.error("❌ Reconnection failed: {}", error.getMessage());
                    return createNewConnection();
                });
        }

        return createNewConnection();
    }

    /**
     * Creates a new connection to replace a failed one.
     */
    private Mono<PooledConnection> createNewConnection() {
        log.info("➕ Creating new connection to replace failed one");
        return createConnection()
                .publishOn(Schedulers.boundedElastic())
            .doOnSuccess(newConn -> {
                if (connections.size() < poolSize) {
                    connections.add(newConn);
                    log.info("✅ New connection added to pool");
                } else {
                    PooledConnection oldest = connections.stream()
                        .filter(conn -> !conn.isHealthy())
                        .findFirst()
                        .orElse(null);

                    if (oldest != null) {
                        connections.remove(oldest);
                        connections.add(newConn);
                        oldest.close().subscribe(); // Cleanup old connection
                        log.info("✅ Replaced unhealthy connection");
                    }
                }
            });
    }

    /**
     * Executes a query using a connection from the pool.
     */
    @Override
    public boolean isConnected() {
        return initialized && connections.stream().anyMatch(PooledConnection::isHealthy);
    }

    @Override
    public <T> Flux<T> query(String sql, Class<T> resultType) {
        return selectConnection()
            .flatMapMany(conn -> conn.getClient().query(sql, resultType)
                .doOnError(error -> {
                    log.error("❌ Query failed on connection: {}", error.getMessage());
                    conn.incrementFailureCount();
                })
                .doOnComplete(conn::resetFailureCount));
    }

    @Override
    public <T> Flux<T> query(String sql, Map<String, Object> params, Class<T> resultType) {
        return selectConnection()
            .flatMapMany(conn -> conn.getClient().query(sql, params, resultType)
                .doOnError(error -> {
                    log.error("❌ Query failed on connection: {}", error.getMessage());
                    conn.incrementFailureCount();
                })
                .doOnComplete(conn::resetFailureCount));
    }

    @Override
    public Flux<Map<String, Object>> listenToLiveQuery(String liveQueryId) {
        return selectConnection()
            .flatMapMany(conn -> conn.getClient().listenToLiveQuery(liveQueryId)
                .doOnError(error -> {
                    log.error("❌ Live query failed on connection: {}", error.getMessage());
                    conn.incrementFailureCount();
                }));
    }

    @Override
    public Mono<String> live(String table, boolean diff) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().live(table, diff)
                .doOnError(error -> {
                    log.error("❌ Live query start failed on connection: {}", error.getMessage());
                    conn.incrementFailureCount();
                }));
    }

    @Override
    public Mono<Void> kill(String liveQueryId) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().kill(liveQueryId)
                .doOnError(error -> {
                    log.error("❌ Kill live query failed on connection: {}", error.getMessage());
                    conn.incrementFailureCount();
                }));
    }


    @Override
    public <T> Mono<T> transaction(Function<OneirosTransaction, Mono<T>> transactionBlock) {
        // For pooled connections, transactions should use a dedicated connection
        return dedicated().transaction(transactionBlock);
    }

    @Override
    public <T> Flux<T> transactionMany(Function<OneirosTransaction, Flux<T>> transactionBlock) {
        // For pooled connections, transactions should use a dedicated connection
        return dedicated().transactionMany(transactionBlock);
    }

    @Override
    public Mono<Void> let(String name, Object value) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().let(name, value)
                .doOnError(error -> {
                    log.error("❌ Let variable failed on connection: {}", error.getMessage());
                    conn.incrementFailureCount();
                }));
    }

    @Override
    public Mono<Void> unset(String name) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().unset(name)
                .doOnError(error -> {
                    log.error("❌ Unset variable failed on connection: {}", error.getMessage());
                    conn.incrementFailureCount();
                }));
    }

    // ============================================================
    // SESSION MANAGEMENT METHODS
    // ============================================================

    @Override
    public Mono<Void> authenticate(String token) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().authenticate(token));
    }

    @Override
    public Mono<String> signin(Map<String, Object> credentials) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().signin(credentials));
    }

    @Override
    public Mono<String> signup(Map<String, Object> credentials) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().signup(credentials));
    }

    @Override
    public Mono<Void> invalidate() {
        return selectConnection()
            .flatMap(conn -> conn.getClient().invalidate());
    }

    @Override
    public <T> Mono<T> info(Class<T> resultType) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().info(resultType));
    }

    @Override
    public Mono<Void> reset() {
        return selectConnection()
            .flatMap(conn -> conn.getClient().reset());
    }

    @Override
    public Mono<Void> ping() {
        return selectConnection()
            .flatMap(conn -> conn.getClient().ping());
    }

    @Override
    public Mono<Map<String, Object>> version() {
        return selectConnection()
            .flatMap(conn -> conn.getClient().version());
    }

    @Override
    public Mono<Void> use(String namespace, String database) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().use(namespace, database));
    }

    // ============================================================
    // CRUD OPERATION METHODS
    // ============================================================

    @Override
    public <T> Flux<T> select(String thing, Class<T> resultType) {
        return selectConnection()
            .flatMapMany(conn -> conn.getClient().select(thing, resultType)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    @Override
    public <T> Flux<T> select(String thing, Map<String, Object> options, Class<T> resultType) {
        return selectConnection()
            .flatMapMany(conn -> conn.getClient().select(thing, options, resultType)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    @Override
    public <T> Mono<T> create(String thing, Object data, Class<T> resultType) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().create(thing, data, resultType)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    @Override
    public <T> Flux<T> insert(String thing, Object data, Class<T> resultType) {
        return selectConnection()
            .flatMapMany(conn -> conn.getClient().insert(thing, data, resultType)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    @Override
    public <T> Flux<T> update(String thing, Object data, Class<T> resultType) {
        return selectConnection()
            .flatMapMany(conn -> conn.getClient().update(thing, data, resultType)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    @Override
    public <T> Flux<T> upsert(String thing, Object data, Class<T> resultType) {
        return selectConnection()
            .flatMapMany(conn -> conn.getClient().upsert(thing, data, resultType)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    @Override
    public <T> Flux<T> merge(String thing, Object data, Class<T> resultType) {
        return selectConnection()
            .flatMapMany(conn -> conn.getClient().merge(thing, data, resultType)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    @Override
    public Flux<Map<String, Object>> patch(String thing, List<Map<String, Object>> patches, boolean returnDiff) {
        return selectConnection()
            .flatMapMany(conn -> conn.getClient().patch(thing, patches, returnDiff)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    @Override
    public <T> Flux<T> delete(String thing, Class<T> resultType) {
        return selectConnection()
            .flatMapMany(conn -> conn.getClient().delete(thing, resultType)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    // ============================================================
    // GRAPH RELATION METHODS
    // ============================================================

    @Override
    public <T> Mono<T> relate(String in, String relation, String out, Object data, Class<T> resultType) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().relate(in, relation, out, data, resultType)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    @Override
    public <T> Mono<T> insertRelation(String table, Object data, Class<T> resultType) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().insertRelation(table, data, resultType)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    // ============================================================
    // ADVANCED QUERY METHODS
    // ============================================================

    @Override
    public Mono<Map<String, Object>> graphql(Object query, Map<String, Object> options) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().graphql(query, options)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    @Override
    public <T> Mono<T> run(String functionName, String version, List<Object> args, Class<T> resultType) {
        return selectConnection()
            .flatMap(conn -> conn.getClient().run(functionName, version, args, resultType)
                .doOnError(error -> conn.incrementFailureCount()));
    }

    /**
     * Closes all connections in the pool.
     */
    @Override
    public Mono<Void> disconnect() {
        log.info("🛑 Shutting down connection pool");

        // Stop health check scheduler
        if (healthCheckScheduler != null && !healthCheckScheduler.isDisposed()) {
            healthCheckScheduler.dispose();
            healthCheckScheduler = null;
        }

        return Flux.fromIterable(connections)
            .flatMap(PooledConnection::close)
            .then()
            .doFinally(signal -> {
                connections.clear();
                initialized = false;
                log.info("✅ Connection pool shut down");
            });
    }

    /**
     * Starts the framework-agnostic health check scheduler.
     * Uses Reactor's interval-based scheduling instead of Spring @Scheduled.
     */
    private void startHealthCheckScheduler() {
        if (healthCheckScheduler != null && !healthCheckScheduler.isDisposed()) {
            return; // Already running
        }

        healthCheckScheduler = Flux.interval(Duration.ofSeconds(healthCheckIntervalSeconds))
            .publishOn(Schedulers.boundedElastic())
            .subscribe(tick -> performHealthCheck());

        log.debug("🏥 Health check scheduler started (interval: {}s)", healthCheckIntervalSeconds);
    }

    /**
     * Health check for all connections.
     * Runs periodically to identify and recover unhealthy connections.
     * This method can also be called manually if needed.
     */
    public void performHealthCheck() {
        if (!initialized) {
            return;
        }

        log.debug("🏥 Performing health check on {} connections", connections.size());

        Flux.fromIterable(connections)
            .flatMap(conn -> conn.healthCheck()
                .doOnNext(healthy -> {
                    if (!healthy) {
                        log.warn("⚠️ Connection unhealthy, will attempt recovery");
                    }
                }))
            .subscribe();
    }

    /**
     * Returns pool statistics.
     */
    public PoolStats getStats() {
        long healthy = connections.stream().filter(PooledConnection::isHealthy).count();
        long unhealthy = connections.stream().filter(conn -> !conn.isHealthy()).count();

        return new PoolStats(
            connections.size(),
            (int) healthy,
            (int) unhealthy,
            poolSize
        );
    }

    /**
     * Returns a dedicated single connection for sequential operations.
     * This always uses the first healthy connection in the pool,
     * ensuring all operations execute on the same connection.
     *
     * <p>Use this for operations that require sequential execution
     * on the same connection, such as schema migrations.
     *
     * @return A wrapper that delegates all calls to a single fixed connection
     */
    @Override
    public OneirosClient dedicated() {
        // Return a wrapper that always uses the first healthy connection
        return new DedicatedConnectionWrapper(this);
    }

    /**
     * Wrapper that provides access to a single dedicated connection from the pool.
     * All operations are routed to the same connection to ensure sequential execution.
     */
    private static class DedicatedConnectionWrapper implements OneirosClient {
        private final OneirosConnectionPool pool;
        private volatile PooledConnection dedicatedConnection;

        DedicatedConnectionWrapper(OneirosConnectionPool pool) {
            this.pool = pool;
        }

        private OneirosClient getOrCreateDedicatedClient() {
            if (dedicatedConnection == null || !dedicatedConnection.isHealthy()) {
                // Find first healthy connection
                dedicatedConnection = pool.connections.stream()
                    .filter(PooledConnection::isHealthy)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No healthy connections available"));
            }
            return dedicatedConnection.getClient();
        }

        @Override
        public Mono<Void> connect() {
            return pool.connect();
        }

        @Override
        public boolean isConnected() {
            return pool.isConnected();
        }

        @Override
        public Mono<Void> disconnect() {
            return pool.disconnect();
        }

        @Override
        public <T> Flux<T> query(String sql, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.query(sql, resultType));
        }

        @Override
        public <T> Flux<T> query(String sql, Map<String, Object> params, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.query(sql, params, resultType));
        }

        @Override
        public <T> Flux<T> select(String thing, Map<String, Object> options, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.select(thing, options, resultType));
        }

        @Override
        public Mono<Void> authenticate(String token) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.authenticate(token));
        }

        @Override
        public Mono<String> signin(Map<String, Object> credentials) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.signin(credentials));
        }

        @Override
        public Mono<String> signup(Map<String, Object> credentials) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.signup(credentials));
        }

        @Override
        public Mono<Void> invalidate() {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(OneirosClient::invalidate);
        }

        @Override
        public <T> Mono<T> info(Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.info(resultType));
        }

        @Override
        public Mono<Void> reset() {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(OneirosClient::reset);
        }

        @Override
        public Mono<Void> ping() {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(OneirosClient::ping);
        }

        @Override
        public Mono<Map<String, Object>> version() {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(OneirosClient::version);
        }

        @Override
        public Mono<Void> use(String namespace, String database) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.use(namespace, database));
        }

        @Override
        public Mono<Void> let(String name, Object value) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.let(name, value));
        }

        @Override
        public Mono<Void> unset(String name) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.unset(name));
        }

        @Override
        public <T> Flux<T> select(String thing, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.select(thing, resultType));
        }

        @Override
        public <T> Mono<T> create(String thing, Object data, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.create(thing, data, resultType));
        }

        @Override
        public <T> Flux<T> insert(String thing, Object data, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.insert(thing, data, resultType));
        }

        @Override
        public <T> Flux<T> upsert(String thing, Object data, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.upsert(thing, data, resultType));
        }

        @Override
        public <T> Flux<T> update(String thing, Object data, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.update(thing, data, resultType));
        }

        @Override
        public <T> Flux<T> merge(String thing, Object data, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.merge(thing, data, resultType));
        }

        @Override
        public Flux<Map<String, Object>> patch(String thing, List<Map<String, Object>> patches, boolean returnDiff) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.patch(thing, patches, returnDiff));
        }

        @Override
        public <T> Flux<T> delete(String thing, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.delete(thing, resultType));
        }

        @Override
        public <T> Mono<T> relate(String in, String relation, String out, Object data, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.relate(in, relation, out, data, resultType));
        }

        @Override
        public <T> Mono<T> insertRelation(String table, Object data, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.insertRelation(table, data, resultType));
        }

        @Override
        public Mono<String> live(String table, boolean diff) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.live(table, diff));
        }

        @Override
        public Flux<Map<String, Object>> listenToLiveQuery(String liveQueryId) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.listenToLiveQuery(liveQueryId));
        }

        @Override
        public Mono<Void> kill(String liveQueryId) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.kill(liveQueryId));
        }

        @Override
        public OneirosClient dedicated() {
            return this; // Already dedicated
        }

        @Override
        public <T> Mono<T> transaction(Function<OneirosTransaction, Mono<T>> transactionBlock) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.transaction(transactionBlock));
        }

        @Override
        public <T> Flux<T> transactionMany(Function<OneirosTransaction, Flux<T>> transactionBlock) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMapMany(client -> client.transactionMany(transactionBlock));
        }

        @Override
        public <T> Mono<T> run(String functionName, String version, List<Object> args, Class<T> resultType) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.run(functionName, version, args, resultType));
        }

        @Override
        public Mono<Map<String, Object>> graphql(Object query, Map<String, Object> options) {
            return Mono.fromCallable(this::getOrCreateDedicatedClient)
                .flatMap(client -> client.graphql(query, options));
        }
    }

    public record PoolStats(
        int total,
        int healthy,
        int unhealthy,
        int maxSize
    ) {
        public double healthPercentage() {
            return total > 0 ? (double) healthy / total * 100 : 0;
        }
    }
}
