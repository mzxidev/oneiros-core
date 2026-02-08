package io.oneiros.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.oneiros.client.OneirosClient;
import io.oneiros.client.OneirosWebsocketClient;
import io.oneiros.graph.OneirosGraph;
import io.oneiros.live.OneirosLiveManager;
import io.oneiros.migration.OneirosMigrationEngine;
import io.oneiros.pool.OneirosConnectionPool;
import io.oneiros.security.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Framework-agnostic builder for creating Oneiros clients and related components.
 * 
 * <p>This builder allows you to use Oneiros without Spring Boot or any other framework.
 * All components are manually assembled through constructor injection.
 * 
 * <h2>Basic Usage (Single Client)</h2>
 * <pre>{@code
 * Oneiros oneiros = OneirosBuilder.create()
 *     .url("ws://localhost:8000/rpc")
 *     .namespace("myns")
 *     .database("mydb")
 *     .username("root")
 *     .password("root")
 *     .build();
 * 
 * // Use the client
 * oneiros.client().query("SELECT * FROM users", User.class)
 *     .subscribe(user -> System.out.println(user));
 * 
 * // Clean up
 * oneiros.close();
 * }</pre>
 * 
 * <h2>Advanced Usage (Connection Pool)</h2>
 * <pre>{@code
 * Oneiros oneiros = OneirosBuilder.create()
 *     .url("ws://localhost:8000/rpc")
 *     .namespace("myns")
 *     .database("mydb")
 *     .username("root")
 *     .password("root")
 *     .poolEnabled(true)
 *     .poolSize(10)
 *     .build();
 * }</pre>
 * 
 * <h2>With Encryption</h2>
 * <pre>{@code
 * Oneiros oneiros = OneirosBuilder.create()
 *     .url("ws://localhost:8000/rpc")
 *     .namespace("myns")
 *     .database("mydb")
 *     .username("root")
 *     .password("root")
 *     .encryptionKey("my-secret-key-32-chars-long!!")
 *     .build();
 * }</pre>
 * 
 * @see Oneiros
 * @see OneirosConfig
 */
public class OneirosBuilder {

    private static final Logger log = LoggerFactory.getLogger(OneirosBuilder.class);
    private static final DateTimeFormatter SURREAL_DATETIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    // Configuration values
    private String url = "ws://localhost:8000/rpc";
    private String namespace;
    private String database;
    private String username;
    private String password;
    private boolean autoConnect = true;

    // Security
    private boolean securityEnabled = false;
    private String encryptionKey = "";

    // Cache
    private boolean cacheEnabled = true;
    private long cacheTtlSeconds = 60;
    private long cacheMaxSize = 10000;

    // Migration
    private boolean migrationEnabled = false; // Disabled by default in builder mode
    private String migrationBasePackage = "";
    private boolean migrationDryRun = false;
    private boolean migrationOverwrite = false;

    // Pool
    private boolean poolEnabled = false;
    private int poolSize = 5;
    private int poolMinIdle = 2;
    private int poolMaxWaitSeconds = 30;
    private long poolHealthCheckInterval = 30;
    private boolean poolAutoReconnect = true;

    // Circuit Breaker
    private boolean circuitBreakerEnabled = true;
    private float circuitBreakerFailureRateThreshold = 50;
    private long circuitBreakerWaitDurationInOpenState = 5;
    private int circuitBreakerPermittedCallsInHalfOpenState = 3;
    private int circuitBreakerSlidingWindowSize = 10;
    private int circuitBreakerMinimumNumberOfCalls = 5;
    private long circuitBreakerSlowCallDurationThreshold = 2000;
    private float circuitBreakerSlowCallRateThreshold = 100;

    // Custom components (optional)
    private ObjectMapper customObjectMapper;
    private CircuitBreaker customCircuitBreaker;

    private OneirosBuilder() {
        // Private constructor, use create()
    }

    /**
     * Creates a new OneirosBuilder instance.
     * @return A new builder
     */
    public static OneirosBuilder create() {
        return new OneirosBuilder();
    }

    /**
     * Creates a builder from an existing OneirosConfig.
     * @param config The configuration to copy
     * @return A new builder initialized with the config values
     */
    public static OneirosBuilder from(OneirosConfig config) {
        OneirosBuilder builder = new OneirosBuilder();
        builder.url = config.getUrl();
        builder.namespace = config.getNamespace();
        builder.database = config.getDatabase();
        builder.username = config.getUsername();
        builder.password = config.getPassword();
        builder.autoConnect = config.isAutoConnect();

        if (config.getSecurity() != null) {
            builder.securityEnabled = config.getSecurity().isEnabled();
            builder.encryptionKey = config.getSecurity().getKey();
        }

        if (config.getCache() != null) {
            builder.cacheEnabled = config.getCache().isEnabled();
            builder.cacheTtlSeconds = config.getCache().getTtlSeconds();
            builder.cacheMaxSize = config.getCache().getMaxSize();
        }

        if (config.getMigration() != null) {
            builder.migrationEnabled = config.getMigration().isEnabled();
            builder.migrationBasePackage = config.getMigration().getBasePackage();
            builder.migrationDryRun = config.getMigration().isDryRun();
            builder.migrationOverwrite = config.getMigration().isOverwrite();
        }

        if (config.getPool() != null) {
            builder.poolEnabled = config.getPool().isEnabled();
            builder.poolSize = config.getPool().getSize();
            builder.poolMinIdle = config.getPool().getMinIdle();
            builder.poolMaxWaitSeconds = config.getPool().getMaxWaitSeconds();
            builder.poolHealthCheckInterval = config.getPool().getHealthCheckInterval();
            builder.poolAutoReconnect = config.getPool().isAutoReconnect();
        }

        if (config.getCircuitBreaker() != null) {
            builder.circuitBreakerEnabled = config.getCircuitBreaker().isEnabled();
            builder.circuitBreakerFailureRateThreshold = config.getCircuitBreaker().getFailureRateThreshold();
            builder.circuitBreakerWaitDurationInOpenState = config.getCircuitBreaker().getWaitDurationInOpenState();
            builder.circuitBreakerPermittedCallsInHalfOpenState = config.getCircuitBreaker().getPermittedCallsInHalfOpenState();
            builder.circuitBreakerSlidingWindowSize = config.getCircuitBreaker().getSlidingWindowSize();
            builder.circuitBreakerMinimumNumberOfCalls = config.getCircuitBreaker().getMinimumNumberOfCalls();
            builder.circuitBreakerSlowCallDurationThreshold = config.getCircuitBreaker().getSlowCallDurationThreshold();
            builder.circuitBreakerSlowCallRateThreshold = config.getCircuitBreaker().getSlowCallRateThreshold();
        }

        return builder;
    }

    // ============================================================
    // CONNECTION SETTINGS
    // ============================================================

    public OneirosBuilder url(String url) {
        this.url = url;
        return this;
    }

    public OneirosBuilder namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public OneirosBuilder database(String database) {
        this.database = database;
        return this;
    }

    public OneirosBuilder username(String username) {
        this.username = username;
        return this;
    }

    public OneirosBuilder password(String password) {
        this.password = password;
        return this;
    }

    public OneirosBuilder autoConnect(boolean autoConnect) {
        this.autoConnect = autoConnect;
        return this;
    }

    // ============================================================
    // SECURITY SETTINGS
    // ============================================================

    public OneirosBuilder encryptionEnabled(boolean enabled) {
        this.securityEnabled = enabled;
        return this;
    }

    public OneirosBuilder encryptionKey(String key) {
        this.encryptionKey = key;
        this.securityEnabled = key != null && !key.isEmpty();
        return this;
    }

    // ============================================================
    // CACHE SETTINGS
    // ============================================================

    public OneirosBuilder cacheEnabled(boolean enabled) {
        this.cacheEnabled = enabled;
        return this;
    }

    public OneirosBuilder cacheTtlSeconds(long ttlSeconds) {
        this.cacheTtlSeconds = ttlSeconds;
        return this;
    }

    public OneirosBuilder cacheMaxSize(long maxSize) {
        this.cacheMaxSize = maxSize;
        return this;
    }

    // ============================================================
    // MIGRATION SETTINGS
    // ============================================================

    public OneirosBuilder migrationEnabled(boolean enabled) {
        this.migrationEnabled = enabled;
        return this;
    }

    /**
     * Set the base package to scan for migrations and @OneirosTable classes.
     * This enables both versioned migrations (OneirosMigration implementations)
     * and schema sync (@OneirosTable annotations).
     *
     * @param basePackage The package to scan (e.g., "com.myapp.db")
     * @return This builder
     */
    public OneirosBuilder migrationBasePackage(String basePackage) {
        this.migrationBasePackage = basePackage;
        this.migrationEnabled = basePackage != null && !basePackage.isEmpty();
        return this;
    }

    /**
     * Alias for {@link #migrationBasePackage(String)}.
     * Set the base package to scan for migrations.
     *
     * @param basePackage The package to scan (e.g., "com.myapp.db")
     * @return This builder
     */
    public OneirosBuilder migrationsPackage(String basePackage) {
        return migrationBasePackage(basePackage);
    }

    public OneirosBuilder migrationDryRun(boolean dryRun) {
        this.migrationDryRun = dryRun;
        return this;
    }

    public OneirosBuilder migrationOverwrite(boolean overwrite) {
        this.migrationOverwrite = overwrite;
        return this;
    }

    // ============================================================
    // POOL SETTINGS
    // ============================================================

    public OneirosBuilder poolEnabled(boolean enabled) {
        this.poolEnabled = enabled;
        return this;
    }

    public OneirosBuilder poolSize(int size) {
        this.poolSize = size;
        return this;
    }

    public OneirosBuilder poolMinIdle(int minIdle) {
        this.poolMinIdle = minIdle;
        return this;
    }

    public OneirosBuilder poolMaxWaitSeconds(int maxWaitSeconds) {
        this.poolMaxWaitSeconds = maxWaitSeconds;
        return this;
    }

    public OneirosBuilder poolHealthCheckInterval(long intervalSeconds) {
        this.poolHealthCheckInterval = intervalSeconds;
        return this;
    }

    public OneirosBuilder poolAutoReconnect(boolean autoReconnect) {
        this.poolAutoReconnect = autoReconnect;
        return this;
    }

    // ============================================================
    // CIRCUIT BREAKER SETTINGS
    // ============================================================

    public OneirosBuilder circuitBreakerEnabled(boolean enabled) {
        this.circuitBreakerEnabled = enabled;
        return this;
    }

    public OneirosBuilder circuitBreakerFailureRateThreshold(float threshold) {
        this.circuitBreakerFailureRateThreshold = threshold;
        return this;
    }

    public OneirosBuilder circuitBreakerWaitDurationInOpenState(long seconds) {
        this.circuitBreakerWaitDurationInOpenState = seconds;
        return this;
    }

    public OneirosBuilder circuitBreakerPermittedCallsInHalfOpenState(int calls) {
        this.circuitBreakerPermittedCallsInHalfOpenState = calls;
        return this;
    }

    public OneirosBuilder circuitBreakerSlidingWindowSize(int size) {
        this.circuitBreakerSlidingWindowSize = size;
        return this;
    }

    public OneirosBuilder circuitBreakerMinimumNumberOfCalls(int calls) {
        this.circuitBreakerMinimumNumberOfCalls = calls;
        return this;
    }

    // ============================================================
    // CUSTOM COMPONENTS
    // ============================================================

    /**
     * Use a custom ObjectMapper instead of the default SurrealDB-compatible one.
     */
    public OneirosBuilder objectMapper(ObjectMapper mapper) {
        this.customObjectMapper = mapper;
        return this;
    }

    /**
     * Use a custom CircuitBreaker instead of creating one.
     */
    public OneirosBuilder circuitBreaker(CircuitBreaker circuitBreaker) {
        this.customCircuitBreaker = circuitBreaker;
        return this;
    }

    // ============================================================
    // BUILD
    // ============================================================

    /**
     * Builds the OneirosConfig from this builder's settings.
     * @return The configuration object
     */
    public OneirosConfig buildConfig() {
        return OneirosConfig.builder()
            .url(url)
            .namespace(namespace)
            .database(database)
            .username(username)
            .password(password)
            .autoConnect(autoConnect)
            .security(OneirosConfig.SecurityConfig.builder()
                .enabled(securityEnabled)
                .key(encryptionKey)
                .build())
            .cache(OneirosConfig.CacheConfig.builder()
                .enabled(cacheEnabled)
                .ttlSeconds(cacheTtlSeconds)
                .maxSize(cacheMaxSize)
                .build())
            .migration(OneirosConfig.MigrationConfig.builder()
                .enabled(migrationEnabled)
                .basePackage(migrationBasePackage)
                .dryRun(migrationDryRun)
                .overwrite(migrationOverwrite)
                .build())
            .pool(OneirosConfig.PoolConfig.builder()
                .enabled(poolEnabled)
                .size(poolSize)
                .minIdle(poolMinIdle)
                .maxWaitSeconds(poolMaxWaitSeconds)
                .healthCheckInterval(poolHealthCheckInterval)
                .autoReconnect(poolAutoReconnect)
                .build())
            .circuitBreaker(OneirosConfig.CircuitBreakerConfig.builder()
                .enabled(circuitBreakerEnabled)
                .failureRateThreshold(circuitBreakerFailureRateThreshold)
                .waitDurationInOpenState(circuitBreakerWaitDurationInOpenState)
                .permittedCallsInHalfOpenState(circuitBreakerPermittedCallsInHalfOpenState)
                .slidingWindowSize(circuitBreakerSlidingWindowSize)
                .minimumNumberOfCalls(circuitBreakerMinimumNumberOfCalls)
                .slowCallDurationThreshold(circuitBreakerSlowCallDurationThreshold)
                .slowCallRateThreshold(circuitBreakerSlowCallRateThreshold)
                .build())
            .build();
    }

    /**
     * Builds a complete Oneiros instance with all components wired up.
     * @return The Oneiros facade providing access to all components
     */
    public Oneiros build() {
        OneirosConfig config = buildConfig();

        // Create ObjectMapper
        ObjectMapper mapper = customObjectMapper != null 
            ? customObjectMapper 
            : createSurrealObjectMapper();

        // Create CircuitBreaker
        CircuitBreaker circuitBreaker = customCircuitBreaker != null
            ? customCircuitBreaker
            : createCircuitBreaker(config);

        // Create CryptoService
        CryptoService cryptoService = new CryptoService(config);

        // Create PasswordHasher (default Argon2)
        io.oneiros.security.PasswordHasher passwordHasher = new io.oneiros.security.PasswordHasher(
            io.oneiros.security.EncryptionType.ARGON2
        );

        // Create SecurityHandler
        io.oneiros.security.OneirosSecurityHandler securityHandler =
            new io.oneiros.security.OneirosSecurityHandler(
                cryptoService,
                passwordHasher,
                securityEnabled
            );

        // Create Client (either pooled or single)
        OneirosClient rawClient;
        if (poolEnabled) {
            log.info("🏊 Creating Oneiros Connection Pool with {} connections", poolSize);
            rawClient = new OneirosConnectionPool(config, mapper, circuitBreaker, poolSize);
        } else {
            log.info("🔌 Creating single Oneiros WebSocket client");
            rawClient = new OneirosWebsocketClient(config, mapper, circuitBreaker);
        }

        // Wrap client with security handler if encryption is enabled
        OneirosClient client;
        if (securityEnabled) {
            log.info("🔐 Wrapping client with security handler (transparent encryption enabled)");
            client = new io.oneiros.client.SecureOneirosClient(rawClient, securityHandler);
        } else {
            client = rawClient;
        }

        // Auto-connect if enabled
        if (autoConnect) {
            log.info("🚀 Auto-connecting to SurrealDB at {}", url);
            client.connect()
                .doOnSuccess(v -> log.info("✅ Connected successfully!"))
                .doOnError(e -> log.error("❌ Connection failed: {}", e.getMessage()))
                .subscribe();
        }

        // Create optional components
        OneirosLiveManager liveManager = new OneirosLiveManager(client, mapper, cryptoService);
        OneirosGraph graph = new OneirosGraph(client, mapper, cryptoService);

        // Create migration engine if enabled
        OneirosMigrationEngine migrationEngine = null;
        if (migrationEnabled && migrationBasePackage != null && !migrationBasePackage.isEmpty()) {
            log.info("🔧 Creating Migration Engine for package: {}", migrationBasePackage);
            migrationEngine = new OneirosMigrationEngine(
                client, 
                migrationBasePackage, 
                true,  // sequential
                migrationDryRun, 
                migrationOverwrite
            );

            // Run migrations after connection
            OneirosMigrationEngine finalMigrationEngine = migrationEngine;
            client.connect()
                .then(finalMigrationEngine.migrate())
                .doOnSuccess(v -> log.info("✅ Migrations completed"))
                .doOnError(e -> log.error("❌ Migrations failed: {}", e.getMessage()))
                .subscribe();
        }

        return new Oneiros(client, mapper, cryptoService, liveManager, graph, migrationEngine, config);
    }

    // ============================================================
    // INTERNAL HELPERS
    // ============================================================

    private CircuitBreaker createCircuitBreaker(OneirosConfig config) {
        if (!circuitBreakerEnabled) {
            // Return a no-op circuit breaker that's always closed
            return CircuitBreakerRegistry.ofDefaults().circuitBreaker("oneiros-noop");
        }

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(circuitBreakerFailureRateThreshold)
            .waitDurationInOpenState(Duration.ofSeconds(circuitBreakerWaitDurationInOpenState))
            .permittedNumberOfCallsInHalfOpenState(circuitBreakerPermittedCallsInHalfOpenState)
            .slidingWindowSize(circuitBreakerSlidingWindowSize)
            .minimumNumberOfCalls(circuitBreakerMinimumNumberOfCalls)
            .slowCallDurationThreshold(Duration.ofMillis(circuitBreakerSlowCallDurationThreshold))
            .slowCallRateThreshold(circuitBreakerSlowCallRateThreshold)
            .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(cbConfig);
        CircuitBreaker breaker = registry.circuitBreaker("oneiros-db-protection");

        // Log state transitions
        breaker.getEventPublisher().onStateTransition(event -> {
            String from = event.getStateTransition().getFromState().toString();
            String to = event.getStateTransition().getToState().toString();
            log.warn("🛡️ [ONEIROS SHIELD] Circuit Breaker state changed: {} → {}", from, to);
        });

        return breaker;
    }

    /**
     * Creates a SurrealDB-compatible ObjectMapper with proper datetime handling.
     */
    public static ObjectMapper createSurrealObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Java 8 Time Support with SurrealDB datetime format
        JavaTimeModule timeModule = new JavaTimeModule();
        timeModule.addSerializer(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider sp) throws IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    // SurrealDB datetime format: d"2024-01-01T00:00:00Z"
                    gen.writeRawValue("d\"" + SURREAL_DATETIME_FORMATTER.format(value) + "\"");
                }
            }
        });
        mapper.registerModule(timeModule);

        // Don't serialize null values
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Don't write dates as timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Ignore unknown properties when deserializing
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }
}

