package io.oneiros.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Configuration
@EnableConfigurationProperties(OneirosProperties.class)
public class OneirosAutoConfiguration {

    // ANSI Colors für die Konsole
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BOLD = "\u001B[1m";

    @Bean
    @ConditionalOnMissingBean
    @org.springframework.context.annotation.Primary
    public ObjectMapper objectMapper() {
        // Use SurrealDB-compatible ObjectMapper with datetime handling
        return OneirosJacksonConfig.createSurrealObjectMapper();
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(OneirosProperties properties) {
        var cbConfig = properties.getCircuitBreaker();

        // Konfiguration des Schutzschilds aus Properties
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(cbConfig.getFailureRateThreshold()) // Wenn X% der Requests fehlschlagen...
                .waitDurationInOpenState(Duration.ofSeconds(cbConfig.getWaitDurationInOpenState())) // ...warte X Sekunden (Cool-down)
                .permittedNumberOfCallsInHalfOpenState(cbConfig.getPermittedCallsInHalfOpenState()) // ...dann teste mit X Requests
                .slidingWindowSize(cbConfig.getSlidingWindowSize()) // ...basierend auf den letzten X Anfragen
                .minimumNumberOfCalls(cbConfig.getMinimumNumberOfCalls()) // ...aber erst ab X Requests bewerten
                .slowCallDurationThreshold(Duration.ofMillis(cbConfig.getSlowCallDurationThreshold()))
                .slowCallRateThreshold(cbConfig.getSlowCallRateThreshold())
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public CircuitBreaker oneirosCircuitBreaker(CircuitBreakerRegistry registry, OneirosProperties properties) {
        var cbConfig = properties.getCircuitBreaker();
        CircuitBreaker breaker = registry.circuitBreaker("oneiros-db-protection");

        if (!cbConfig.isEnabled()) {
            log.info("🛡️ Circuit Breaker is DISABLED");
            return breaker;
        }

        log.debug("🛡️ Circuit Breaker Configuration:");
        log.debug("   📊 Failure rate threshold: {}%", cbConfig.getFailureRateThreshold());
        log.debug("   ⏱️ Wait duration in open state: {}s", cbConfig.getWaitDurationInOpenState());
        log.debug("   🔄 Permitted calls in half-open: {}", cbConfig.getPermittedCallsInHalfOpenState());
        log.debug("   📏 Sliding window size: {}", cbConfig.getSlidingWindowSize());
        log.debug("   🔢 Minimum number of calls: {}", cbConfig.getMinimumNumberOfCalls());

        // Custom Logging Design für State-Änderungen
        breaker.getEventPublisher().onStateTransition(event -> {
            String from = event.getStateTransition().getFromState().toString();
            String to = event.getStateTransition().getToState().toString();

            // Farbe basierend auf Zustand wählen
            String color = YELLOW; // Standard Warnung
            if ("CLOSED".equals(to)) color = GREEN; // Alles gut -> Grün
            if ("OPEN".equals(to)) color = RED + BOLD; // ALARM -> Rot Fett

            // Das exakte Format, das du wolltest:
            System.out.println(color + "[ONEIROS SHIELD] 🛡️ State changed from " + from + " to " + to + RESET);
        });

        return breaker;
    }

    @Bean
    @ConditionalOnMissingBean
    public CryptoService cryptoService(OneirosProperties properties) {
        return new CryptoService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.oneiros.security.PasswordHasher passwordHasher() {
        return new io.oneiros.security.PasswordHasher(io.oneiros.security.EncryptionType.ARGON2);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.oneiros.security.OneirosSecurityHandler securityHandler(
            OneirosProperties properties,
            CryptoService cryptoService,
            io.oneiros.security.PasswordHasher passwordHasher) {
        boolean securityEnabled = properties.getSecurity() != null && properties.getSecurity().isEnabled();
        return new io.oneiros.security.OneirosSecurityHandler(cryptoService, passwordHasher, securityEnabled);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "oneiros.pool.enabled", havingValue = "false", matchIfMissing = true)
    public OneirosClient oneirosClient(
            OneirosProperties properties,
            ObjectMapper mapper,
            CircuitBreaker breaker,
            io.oneiros.security.OneirosSecurityHandler securityHandler) {

        OneirosWebsocketClient rawClient = new OneirosWebsocketClient(properties, mapper, breaker);

        // Wrap with security handler if encryption is enabled
        OneirosClient client;
        boolean securityEnabled = properties.getSecurity() != null && properties.getSecurity().isEnabled();
        if (securityEnabled) {
            log.info("🔐 Wrapping client with security handler (transparent encryption enabled)");
            client = new io.oneiros.client.SecureOneirosClient(rawClient, securityHandler);
        } else {
            client = rawClient;
        }

        // 🔥 AUTO-CONNECT: Establish connection immediately on startup
        if (properties.isAutoConnect()) {
            System.out.println(GREEN + "🚀 Oneiros auto-connecting to SurrealDB..." + RESET);
            client.connect()
                .doOnSuccess(v -> System.out.println(GREEN + "✅ Oneiros connected successfully!" + RESET))
                .doOnError(e -> System.err.println(RED + "❌ Oneiros connection failed: " + e.getMessage() + RESET))
                .subscribe();
        } else {
            System.out.println(YELLOW + "⏳ Oneiros will connect on first request (lazy mode)" + RESET);
        }

        return client;
    }

    /**
     * Connection Pool bean - manages multiple WebSocket connections for load balancing.
     * Enabled with: oneiros.pool.enabled=true
     */
    @Bean
    @ConditionalOnProperty(name = "oneiros.pool.enabled", havingValue = "true")
    public OneirosClient oneirosConnectionPool(
            OneirosProperties properties,
            ObjectMapper mapper,
            CircuitBreaker breaker,
            io.oneiros.security.OneirosSecurityHandler securityHandler) {

        int poolSize = properties.getPool().getSize();

        log.info("🏊 Initializing Oneiros Connection Pool");
        log.info("   📊 Pool size: {}", poolSize);
        log.info("   🔄 Auto-reconnect: {}", properties.getPool().isAutoReconnect());
        log.info("   ❤️ Health check interval: {}s", properties.getPool().getHealthCheckInterval());

        OneirosConnectionPool rawPool = new OneirosConnectionPool(properties, mapper, breaker, poolSize);

        // Wrap with security handler if encryption is enabled
        OneirosClient pool;
        boolean securityEnabled = properties.getSecurity() != null && properties.getSecurity().isEnabled();
        if (securityEnabled) {
            log.info("🔐 Wrapping connection pool with security handler (transparent encryption enabled)");
            pool = new io.oneiros.client.SecureOneirosClient(rawPool, securityHandler);
        } else {
            pool = rawPool;
        }

        // 🔥 NON-BLOCKING AUTO-CONNECT: Start connections in background
        // The WebSocket sessions must stay open, so we cannot block here
        pool.connect().subscribe(
                null, // onNext
                error -> log.error("❌ Connection pool initialization failed: {}", error.getMessage(), error),
                () -> log.info("✅ Connection pool initialized")
        );

        return pool;
    }

    /**
     * Live Query Manager bean - manages LIVE SELECT subscriptions.
     */
    @Bean
    @ConditionalOnMissingBean
    public OneirosLiveManager oneirosLiveManager(OneirosClient client, ObjectMapper mapper, CryptoService crypto) {
        log.debug("🔴 Initializing Oneiros Live Manager");
        return new OneirosLiveManager(client, mapper, crypto);
    }

    /**
     * Graph API bean for fluent RELATE statement building.
     */
    @Bean
    @ConditionalOnMissingBean
    public OneirosGraph oneirosGraph(OneirosClient client, ObjectMapper mapper, CryptoService crypto) {
        log.debug("🔗 Initializing Oneiros Graph API");
        return new OneirosGraph(client, mapper, crypto);
    }

    /**
     * Migration Engine bean - auto-generates schema from @OneirosEntity classes.
     * Enabled by default, can be disabled with: oneiros.migration.enabled=false
     *
     * <p>Properties:
     * <ul>
     *   <li>oneiros.migration.enabled - Enable/disable migrations (default: true)</li>
     *   <li>oneiros.migration.base-package - Package to scan (default: io.oneiros)</li>
     *   <li>oneiros.migration.dry-run - Log SQL without executing (default: false)</li>
     *   <li>oneiros.migration.overwrite - Use OVERWRITE instead of IF NOT EXISTS (default: false)</li>
     * </ul>
     */
    @Bean
    @ConditionalOnProperty(name = "oneiros.migration.enabled", havingValue = "true", matchIfMissing = true)
    public OneirosMigrationEngine migrationEngine(
            OneirosClient client,
            OneirosProperties properties) {

        OneirosProperties.Migration migrationProps = properties.getMigration();
        String basePackage = migrationProps.getBasePackage();
        boolean dryRun = migrationProps.isDryRun();
        boolean overwrite = migrationProps.isOverwrite();

        log.info("🔧 Initializing Oneiros Migration Engine");
        log.info("   📦 Base package: {}", basePackage);
        log.info("   🧪 Dry run: {}", dryRun);
        if (overwrite) {
            log.info("   🔄 Overwrite mode: ENABLED (will update existing schema definitions)");
        }

        OneirosMigrationEngine engine = new OneirosMigrationEngine(client, basePackage, true, dryRun, overwrite);

        // Wait for pool to be ready before running migrations
        Mono<Void> waitForReady = client instanceof io.oneiros.pool.OneirosConnectionPool
            ? ((io.oneiros.pool.OneirosConnectionPool) client).waitUntilReady()
            : client.connect();

        // Execute migrations after client is ready
        waitForReady
            .then(engine.migrate())
            .doOnSuccess(v -> log.info("✅ Schema migration completed"))
            .doOnError(e -> log.error("❌ Schema migration failed", e))
            .subscribe();

        return engine;
    }
}