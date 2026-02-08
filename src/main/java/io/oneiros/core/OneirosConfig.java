package io.oneiros.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Framework-agnostic configuration for Oneiros client.
 * This POJO holds all connection and behavior settings.
 *
 * <p>Usage (without Spring):
 * <pre>{@code
 * OneirosConfig config = OneirosConfig.builder()
 *     .url("ws://localhost:8000/rpc")
 *     .namespace("myns")
 *     .database("mydb")
 *     .username("root")
 *     .password("root")
 *     .build();
 * }</pre>
 *
 * <p>Usage (with Spring):
 * Configure via application.yml with prefix "oneiros"
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneirosConfig {

    // Connection settings
    @Builder.Default
    private String url = "ws://localhost:8000/rpc";

    private String namespace;
    private String database;
    private String username;
    private String password;

    @Builder.Default
    private boolean autoConnect = true;

    // Security settings
    @Builder.Default
    private SecurityConfig security = SecurityConfig.builder().build();

    // Cache settings
    @Builder.Default
    private CacheConfig cache = CacheConfig.builder().build();

    // Migration settings
    @Builder.Default
    private MigrationConfig migration = MigrationConfig.builder().build();

    // Pool settings
    @Builder.Default
    private PoolConfig pool = PoolConfig.builder().build();

    // Circuit Breaker settings
    @Builder.Default
    private CircuitBreakerConfig circuitBreaker = CircuitBreakerConfig.builder().build();

    /**
     * Security configuration for encryption/hashing.
     */
    @Getter
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecurityConfig {
        @Builder.Default
        private boolean enabled = false;

        @Builder.Default
        private String key = "";
    }

    /**
     * Local cache configuration.
     */
    @Getter
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CacheConfig {
        @Builder.Default
        private boolean enabled = true;

        @Builder.Default
        private long ttlSeconds = 60;

        @Builder.Default
        private long maxSize = 10000;
    }

    /**
     * Schema migration configuration.
     */
    @Getter
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MigrationConfig {
        @Builder.Default
        private boolean enabled = true;

        @Builder.Default
        private String basePackage = "io.oneiros";

        @Builder.Default
        private boolean dryRun = false;

        @Builder.Default
        private boolean overwrite = false;
    }

    /**
     * Connection pool configuration.
     */
    @Getter
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PoolConfig {
        @Builder.Default
        private boolean enabled = false;

        @Builder.Default
        private int size = 5;

        @Builder.Default
        private int minIdle = 2;

        @Builder.Default
        private int maxWaitSeconds = 30;

        @Builder.Default
        private long healthCheckInterval = 30;

        @Builder.Default
        private boolean autoReconnect = true;
    }

    /**
     * Circuit Breaker configuration for database protection.
     */
    @Getter
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CircuitBreakerConfig {
        @Builder.Default
        private boolean enabled = true;

        @Builder.Default
        private float failureRateThreshold = 50;

        @Builder.Default
        private long waitDurationInOpenState = 5;

        @Builder.Default
        private int permittedCallsInHalfOpenState = 3;

        @Builder.Default
        private int slidingWindowSize = 10;

        @Builder.Default
        private int minimumNumberOfCalls = 5;

        @Builder.Default
        private long slowCallDurationThreshold = 2000;

        @Builder.Default
        private float slowCallRateThreshold = 100;
    }

    /**
     * Creates an OneirosConfig from legacy OneirosProperties (for Spring compatibility).
     */
    public static OneirosConfig fromProperties(io.oneiros.config.OneirosProperties properties) {
        return OneirosConfig.builder()
            .url(properties.getUrl())
            .namespace(properties.getNamespace())
            .database(properties.getDatabase())
            .username(properties.getUsername())
            .password(properties.getPassword())
            .autoConnect(properties.isAutoConnect())
            .security(SecurityConfig.builder()
                .enabled(properties.getSecurity().isEnabled())
                .key(properties.getSecurity().getKey())
                .build())
            .cache(CacheConfig.builder()
                .enabled(properties.getCache().isEnabled())
                .ttlSeconds(properties.getCache().getTtlSeconds())
                .maxSize(properties.getCache().getMaxSize())
                .build())
            .migration(MigrationConfig.builder()
                .enabled(properties.getMigration().isEnabled())
                .basePackage(properties.getMigration().getBasePackage())
                .dryRun(properties.getMigration().isDryRun())
                .overwrite(properties.getMigration().isOverwrite())
                .build())
            .pool(PoolConfig.builder()
                .enabled(properties.getPool().isEnabled())
                .size(properties.getPool().getSize())
                .minIdle(properties.getPool().getMinIdle())
                .maxWaitSeconds(properties.getPool().getMaxWaitSeconds())
                .healthCheckInterval(properties.getPool().getHealthCheckInterval())
                .autoReconnect(properties.getPool().isAutoReconnect())
                .build())
            .circuitBreaker(CircuitBreakerConfig.builder()
                .enabled(properties.getCircuitBreaker().isEnabled())
                .failureRateThreshold(properties.getCircuitBreaker().getFailureRateThreshold())
                .waitDurationInOpenState(properties.getCircuitBreaker().getWaitDurationInOpenState())
                .permittedCallsInHalfOpenState(properties.getCircuitBreaker().getPermittedCallsInHalfOpenState())
                .slidingWindowSize(properties.getCircuitBreaker().getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getCircuitBreaker().getMinimumNumberOfCalls())
                .slowCallDurationThreshold(properties.getCircuitBreaker().getSlowCallDurationThreshold())
                .slowCallRateThreshold(properties.getCircuitBreaker().getSlowCallRateThreshold())
                .build())
            .build();
    }
}