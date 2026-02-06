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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@EnableConfigurationProperties(OneirosProperties.class)
@org.springframework.scheduling.annotation.EnableScheduling
public class OneirosAutoConfiguration {

    // ANSI Colors für die Konsole
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BOLD = "\u001B[1m";

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // Konfiguration des Schutzschilds
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Wenn 50% der Requests fehlschlagen...
                .waitDurationInOpenState(Duration.ofSeconds(5)) // ...warte 5 Sekunden (Cool-down)
                .permittedNumberOfCallsInHalfOpenState(3) // ...dann teste mit 3 Requests
                .slidingWindowSize(10) // ...basierend auf den letzten 10 Anfragen
                .minimumNumberOfCalls(5) // ...aber erst ab 5 Requests bewerten
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public CircuitBreaker oneirosCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker breaker = registry.circuitBreaker("oneiros-db-protection");

        // HIER ist dein Custom Logging Design
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
    @ConditionalOnProperty(name = "oneiros.pool.enabled", havingValue = "false", matchIfMissing = true)
    public OneirosClient oneirosClient(OneirosProperties properties, ObjectMapper mapper, CircuitBreaker breaker) {
        OneirosWebsocketClient client = new OneirosWebsocketClient(properties, mapper, breaker);

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
            CircuitBreaker breaker) {

        int poolSize = properties.getPool().getSize();

        log.info("🏊 Initializing Oneiros Connection Pool");
        log.info("   📊 Pool size: {}", poolSize);
        log.info("   🔄 Auto-reconnect: {}", properties.getPool().isAutoReconnect());
        log.info("   ❤️ Health check interval: {}s", properties.getPool().getHealthCheckInterval());

        OneirosConnectionPool pool = new OneirosConnectionPool(properties, mapper, breaker, poolSize);

        // 🔥 AUTO-CONNECT: Always establish pool connections on startup
        pool.connect()
            .doOnSuccess(v -> log.info("✅ Connection pool initialized and connected"))
            .doOnError(e -> log.error("❌ Connection pool initialization failed", e))
            .subscribe();

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
     */
    @Bean
    @ConditionalOnProperty(name = "oneiros.migration.enabled", havingValue = "true", matchIfMissing = true)
    public OneirosMigrationEngine migrationEngine(
            OneirosClient client,
            @Value("${oneiros.migration.base-package:io.oneiros}") String basePackage,
            @Value("${oneiros.migration.dry-run:false}") boolean dryRun) {

        log.info("🔧 Initializing Oneiros Migration Engine");
        log.info("   📦 Base package: {}", basePackage);
        log.info("   🧪 Dry run: {}", dryRun);

        OneirosMigrationEngine engine = new OneirosMigrationEngine(client, basePackage, true, dryRun);

        // Execute migrations after client is connected
        client.connect()
            .then(engine.migrate())
            .doOnSuccess(v -> log.info("✅ Schema migration completed"))
            .doOnError(e -> log.error("❌ Schema migration failed", e))
            .subscribe();

        return engine;
    }
}