package io.oneiros.pool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.oneiros.client.OneirosClient;
import io.oneiros.client.OneirosWebsocketClient;
import io.oneiros.config.OneirosProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connection pool for managing multiple WebSocket connections to SurrealDB.
 * Provides load balancing, health checking, and automatic reconnection.
 */
public class OneirosConnectionPool implements OneirosClient {

    private static final Logger log = LoggerFactory.getLogger(OneirosConnectionPool.class);

    private final OneirosProperties properties;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;
    private final int poolSize;

    private final List<PooledConnection> connections = new CopyOnWriteArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    private volatile boolean initialized = false;

    public OneirosConnectionPool(
            OneirosProperties properties,
            ObjectMapper objectMapper,
            CircuitBreaker circuitBreaker,
            int poolSize
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreaker;
        this.poolSize = poolSize;
    }

    /**
     * Initializes the connection pool by creating all connections.
     */
    @Override
    public Mono<Void> connect() {
        if (initialized) {
            return Mono.empty();
        }

        log.info("🏊 Initializing connection pool with {} connections", poolSize);

        return Flux.range(0, poolSize)
            .flatMap(i -> createConnection()
                .doOnSuccess(conn -> {
                    connections.add(conn);
                    log.info("✅ Connection #{} added to pool", i + 1);
                })
                .onErrorResume(error -> {
                    log.error("❌ Failed to create connection #{}: {}", i + 1, error.getMessage());
                    return Mono.empty();
                }))
            .then()
            .doOnSuccess(v -> {
                initialized = true;
                log.info("🏊 Connection pool initialized with {}/{} connections",
                    connections.size(), poolSize);
            });
    }

    /**
     * Creates a new pooled connection.
     */
    private Mono<PooledConnection> createConnection() {
        OneirosWebsocketClient client = new OneirosWebsocketClient(
            properties,
            objectMapper,
            circuitBreaker
        );

        return client.connect()
            .thenReturn(new PooledConnection(client));
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
     * Scheduled health check for all connections.
     * Runs every 30 seconds to identify and recover unhealthy connections.
     */
    @Scheduled(fixedRate = 30000)
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
