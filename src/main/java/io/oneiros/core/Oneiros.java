package io.oneiros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.oneiros.client.OneirosClient;
import io.oneiros.client.OneirosWebsocketClient;
import io.oneiros.config.OneirosJacksonConfig;
import io.oneiros.graph.OneirosGraph;
import io.oneiros.live.OneirosLiveManager;
import io.oneiros.migration.OneirosMigrationEngine;
import io.oneiros.pool.OneirosConnectionPool;
import io.oneiros.security.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Builder and Factory for creating Oneiros components without a framework.
 * This is the main entry point for using Oneiros in non-Spring applications.
 *
 * <p><strong>Quick Start:</strong>
 * <pre>{@code
 * // Create and connect
 * Oneiros oneiros = Oneiros.builder()
 *     .url("ws://localhost:8000/rpc")
 *     .namespace("test")
 *     .database("test")
 *     .username("root")
 *     .password("root")
 *     .build();
 *
 * // Use the client
 * oneiros.client().query("SELECT * FROM users", User.class)
 *     .doOnNext(System.out::println)
 *     .subscribe();
 *
 * // Or use the blocking convenience method
 * List<User> users = oneiros.client().query("SELECT * FROM users", User.class)
 *     .collectList()
 *     .block();
 * }</pre>
 *
 * <p><strong>With Connection Pool:</strong>
 * <pre>{@code
 * Oneiros oneiros = Oneiros.builder()
 *     .url("ws://localhost:8000/rpc")
 *     .namespace("test")
 *     .database("test")
 *     .username("root")
 *     .password("root")
 *     .poolEnabled(true)
 *     .poolSize(10)
 *     .build();
 * }</pre>
 */
public class Oneiros implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Oneiros.class);

    private final io.oneiros.core.OneirosConfig config;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;
    private final OneirosClient client;
    private final CryptoService cryptoService;
    private final OneirosLiveManager liveManager;
    private final OneirosGraph graph;
    private final OneirosMigrationEngine migrationEngine;

    /**
     * Constructor for direct instantiation with all components.
     * Used by OneirosBuilder for maximum flexibility.
     */
    public Oneiros(
            OneirosClient client,
            ObjectMapper objectMapper,
            CryptoService cryptoService,
            OneirosLiveManager liveManager,
            OneirosGraph graph,
            OneirosMigrationEngine migrationEngine,
            OneirosConfig config
    ) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
        this.liveManager = liveManager;
        this.graph = graph;
        this.migrationEngine = migrationEngine;
        this.config = config;
        this.circuitBreaker = null; // External management
    }

    private Oneiros(Builder builder) {
        this.config = builder.config;
        this.objectMapper = builder.objectMapper != null
            ? builder.objectMapper
            : OneirosJacksonConfig.createSurrealObjectMapper();

        this.circuitBreaker = createCircuitBreaker(config.getCircuitBreaker(), builder.circuitBreakerStateListener);
        this.cryptoService = new CryptoService(config);

        // Create client (single or pooled)
        if (config.getPool().isEnabled()) {
            log.info("🏊 Creating connection pool with {} connections", config.getPool().getSize());
            this.client = new OneirosConnectionPool(config, objectMapper, circuitBreaker, config.getPool().getSize());
        } else {
            log.info("🔌 Creating single WebSocket connection");
            this.client = new OneirosWebsocketClient(config, objectMapper, circuitBreaker);
        }

        // Create dependent services
        this.liveManager = new OneirosLiveManager(client, objectMapper, cryptoService);
        this.graph = new OneirosGraph(client, objectMapper, cryptoService);

        // Create migration engine if enabled
        if (config.getMigration().isEnabled()) {
            this.migrationEngine = new OneirosMigrationEngine(
                client,
                config.getMigration().getBasePackage(),
                true,
                config.getMigration().isDryRun(),
                config.getMigration().isOverwrite()
            );
        } else {
            this.migrationEngine = null;
        }

        // Auto-connect if configured
        if (config.isAutoConnect()) {
            log.info("🚀 Auto-connecting to SurrealDB...");
            client.connect()
                .doOnSuccess(v -> log.info("✅ Connected to SurrealDB!"))
                .doOnError(e -> log.error("❌ Connection failed: {}", e.getMessage()))
                .subscribe();
        }
    }

    /**
     * Creates a new Oneiros builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an Oneiros instance from an existing config.
     */
    public static Oneiros create(io.oneiros.core.OneirosConfig config) {
        return builder().config(config).build();
    }

    // ============== Getters ==============

    /**
     * Returns the main database client.
     */
    public OneirosClient client() {
        return client;
    }

    /**
     * Returns the crypto service for encryption/hashing.
     */
    public CryptoService crypto() {
        return cryptoService;
    }

    /**
     * Returns the live query manager.
     */
    public OneirosLiveManager live() {
        return liveManager;
    }

    /**
     * Returns the graph API for relations.
     */
    public OneirosGraph graph() {
        return graph;
    }

    /**
     * Returns the migration engine (null if disabled).
     */
    public OneirosMigrationEngine migrations() {
        return migrationEngine;
    }

    /**
     * Returns the circuit breaker.
     */
    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Returns the ObjectMapper used for JSON serialization.
     */
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    /**
     * Returns the configuration.
     */
    public io.oneiros.core.OneirosConfig config() {
        return config;
    }

    // ============== Lifecycle ==============

    /**
     * Connects to SurrealDB (if not already connected).
     */
    public Mono<Void> connect() {
        return client.connect();
    }

    /**
     * Connects to SurrealDB and blocks until connected.
     */
    public void connectBlocking() {
        client.connect().block();
    }

    /**
     * Runs schema migrations if enabled.
     */
    public Mono<Void> migrate() {
        if (migrationEngine == null) {
            log.warn("Migration engine is disabled");
            return Mono.empty();
        }
        return migrationEngine.migrate();
    }

    /**
     * Runs schema migrations and blocks until complete.
     */
    public void migrateBlocking() {
        migrate().block();
    }

    /**
     * Disconnects from SurrealDB.
     */
    public Mono<Void> disconnect() {
        return client.disconnect();
    }

    /**
     * Disconnects from SurrealDB and blocks until disconnected.
     */
    public void disconnectBlocking() {
        disconnect().block();
    }

    /**
     * Closes this Oneiros instance (alias for disconnectBlocking).
     * Implements AutoCloseable for try-with-resources support.
     */
    @Override
    public void close() {
        disconnectBlocking();
    }

    // ============== Private Helpers ==============

    private CircuitBreaker createCircuitBreaker(
            io.oneiros.core.OneirosConfig.CircuitBreakerConfig cbConfig,
            Consumer<CircuitBreaker.StateTransition> stateListener
    ) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(cbConfig.getFailureRateThreshold())
            .waitDurationInOpenState(Duration.ofSeconds(cbConfig.getWaitDurationInOpenState()))
            .permittedNumberOfCallsInHalfOpenState(cbConfig.getPermittedCallsInHalfOpenState())
            .slidingWindowSize(cbConfig.getSlidingWindowSize())
            .minimumNumberOfCalls(cbConfig.getMinimumNumberOfCalls())
            .slowCallDurationThreshold(Duration.ofMillis(cbConfig.getSlowCallDurationThreshold()))
            .slowCallRateThreshold(cbConfig.getSlowCallRateThreshold())
            .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker breaker = registry.circuitBreaker("oneiros-db-protection");

        if (cbConfig.isEnabled() && stateListener != null) {
            breaker.getEventPublisher().onStateTransition(event -> {
                stateListener.accept(event.getStateTransition());
            });
        }

        if (!cbConfig.isEnabled()) {
            log.info("🛡️ Circuit Breaker is DISABLED");
        } else {
            log.debug("🛡️ Circuit Breaker Configuration:");
            log.debug("   📊 Failure rate threshold: {}%", cbConfig.getFailureRateThreshold());
            log.debug("   ⏱️ Wait duration in open state: {}s", cbConfig.getWaitDurationInOpenState());
        }

        return breaker;
    }

    // ============== Builder ==============

    public static class Builder {
        private io.oneiros.core.OneirosConfig config;
        private ObjectMapper objectMapper;
        private Consumer<CircuitBreaker.StateTransition> circuitBreakerStateListener;

        // Individual config fields for convenience
        private String url = "ws://localhost:8000/rpc";
        private String namespace;
        private String database;
        private String username;
        private String password;
        private boolean autoConnect = true;

        // Security
        private boolean securityEnabled = false;
        private String securityKey = "";

        // Pool
        private boolean poolEnabled = false;
        private int poolSize = 5;
        private boolean poolAutoReconnect = true;
        private long poolHealthCheckInterval = 30;

        // Migration
        private boolean migrationEnabled = true;
        private String migrationBasePackage = "io.oneiros";
        private boolean migrationDryRun = false;
        private boolean migrationOverwrite = false;

        // Circuit Breaker
        private boolean circuitBreakerEnabled = true;
        private float circuitBreakerFailureRate = 50;

        /**
         * Uses an existing OneirosConfig.
         */
        public Builder config(io.oneiros.core.OneirosConfig config) {
            this.config = config;
            return this;
        }

        /**
         * Uses a custom ObjectMapper.
         */
        public Builder objectMapper(ObjectMapper mapper) {
            this.objectMapper = mapper;
            return this;
        }

        /**
         * Adds a listener for circuit breaker state changes.
         */
        public Builder onCircuitBreakerStateChange(Consumer<CircuitBreaker.StateTransition> listener) {
            this.circuitBreakerStateListener = listener;
            return this;
        }

        // Connection settings
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder autoConnect(boolean autoConnect) {
            this.autoConnect = autoConnect;
            return this;
        }

        // Security settings
        public Builder securityEnabled(boolean enabled) {
            this.securityEnabled = enabled;
            return this;
        }

        public Builder securityKey(String key) {
            this.securityKey = key;
            return this;
        }

        // Pool settings
        public Builder poolEnabled(boolean enabled) {
            this.poolEnabled = enabled;
            return this;
        }

        public Builder poolSize(int size) {
            this.poolSize = size;
            return this;
        }

        public Builder poolAutoReconnect(boolean autoReconnect) {
            this.poolAutoReconnect = autoReconnect;
            return this;
        }

        public Builder poolHealthCheckInterval(long seconds) {
            this.poolHealthCheckInterval = seconds;
            return this;
        }

        // Migration settings
        public Builder migrationEnabled(boolean enabled) {
            this.migrationEnabled = enabled;
            return this;
        }

        public Builder migrationBasePackage(String basePackage) {
            this.migrationBasePackage = basePackage;
            return this;
        }

        public Builder migrationDryRun(boolean dryRun) {
            this.migrationDryRun = dryRun;
            return this;
        }

        public Builder migrationOverwrite(boolean overwrite) {
            this.migrationOverwrite = overwrite;
            return this;
        }

        // Circuit Breaker settings
        public Builder circuitBreakerEnabled(boolean enabled) {
            this.circuitBreakerEnabled = enabled;
            return this;
        }

        public Builder circuitBreakerFailureRate(float rate) {
            this.circuitBreakerFailureRate = rate;
            return this;
        }

        /**
         * Builds the Oneiros instance.
         */
        public Oneiros build() {
            // Build config from individual fields if not provided
            if (this.config == null) {
                this.config = io.oneiros.core.OneirosConfig.builder()
                    .url(url)
                    .namespace(namespace)
                    .database(database)
                    .username(username)
                    .password(password)
                    .autoConnect(autoConnect)
                    .security(io.oneiros.core.OneirosConfig.SecurityConfig.builder()
                        .enabled(securityEnabled)
                        .key(securityKey)
                        .build())
                    .pool(io.oneiros.core.OneirosConfig.PoolConfig.builder()
                        .enabled(poolEnabled)
                        .size(poolSize)
                        .autoReconnect(poolAutoReconnect)
                        .healthCheckInterval(poolHealthCheckInterval)
                        .build())
                    .migration(io.oneiros.core.OneirosConfig.MigrationConfig.builder()
                        .enabled(migrationEnabled)
                        .basePackage(migrationBasePackage)
                        .dryRun(migrationDryRun)
                        .overwrite(migrationOverwrite)
                        .build())
                    .circuitBreaker(io.oneiros.core.OneirosConfig.CircuitBreakerConfig.builder()
                        .enabled(circuitBreakerEnabled)
                        .failureRateThreshold(circuitBreakerFailureRate)
                        .build())
                    .build();
            }

            Objects.requireNonNull(config.getUrl(), "URL must not be null");
            Objects.requireNonNull(config.getUsername(), "Username must not be null");
            Objects.requireNonNull(config.getPassword(), "Password must not be null");

            return new Oneiros(this);
        }
    }
}

