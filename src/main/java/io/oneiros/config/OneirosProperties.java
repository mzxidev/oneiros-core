package io.oneiros.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Setter
@Getter
@ConfigurationProperties(prefix = "oneiros")
public class OneirosProperties {

    // Getter und Setter (Lombok @Data wäre hier praktisch, wenn du es nutzt, sonst generieren)
    /**
     * Die WebSocket URL zur SurrealDB Instanz.
     * Beispiel: ws://localhost:8000/rpc
     */
    private String url = "ws://localhost:8000/rpc";

    /**
     * Der Namespace in SurrealDB.
     */
    private String namespace;

    /**
     * Der Datenbank-Name in SurrealDB.
     */
    private String database;

    /**
     * Der Benutzername für die Authentifizierung.
     */
    private String username;

    /**
     * Das Passwort für die Authentifizierung.
     */
    private String password;

    /**
     * Auto-connect on startup (default: true).
     * If true, connection to SurrealDB is established immediately when the application starts.
     * If false, connection is established lazily on the first request.
     */
    private boolean autoConnect = true;

    private Security security = new Security();

    @Setter
    @Getter
    public static class Security {
        private boolean enabled = false; // Standardmäßig aus
        private String key = ""; // Der geheime Schlüssel (Muss > 16 Zeichen sein)

    }

    private Cache cache = new Cache();

    private Migration migration = new Migration();

    private Pool pool = new Pool();

    @Setter
    @Getter
    public static class Cache {
        private boolean enabled = true;
        private long ttlSeconds = 60;
        private long maxSize = 10000;
    }

    @Setter
    @Getter
    public static class Migration {
        private boolean enabled = true;
        private String basePackage = "io.oneiros";
        private boolean dryRun = false;
        /**
         * Use OVERWRITE instead of IF NOT EXISTS for schema definitions.
         *
         * <p><strong>⚠️ IMPORTANT:</strong> Set to {@code true} when you encounter errors like:
         * <ul>
         *   <li>"There was a problem running the length() function" - Old assertions use .length() instead of string::len()</li>
         *   <li>"Failed to commit transaction due to a read or write conflict"</li>
         *   <li>Any field type or constraint changes</li>
         * </ul>
         *
         * <p>When to use:
         * <ul>
         *   <li>You updated @OneirosField assertions from JavaScript-style to SurrealDB functions</li>
         *   <li>You changed field types or constraints</li>
         *   <li>You're upgrading from an older Oneiros version</li>
         * </ul>
         *
         * <p>Example in application.yml:
         * <pre>
         * oneiros:
         *   migration:
         *     overwrite: true  # Set to true to update existing schema
         * </pre>
         *
         * <p>Alternative: Manually drop tables in SurrealDB:
         * <pre>
         * REMOVE TABLE tablename;
         * </pre>
         *
         * <p>Default: false (use IF NOT EXISTS for backwards compatibility and safety)
         */
        private boolean overwrite = false;
    }

    @Setter
    @Getter
    public static class Pool {
        private boolean enabled = false;
        private int size = 5;
        private int minIdle = 2;
        private int maxWaitSeconds = 30;
        private long healthCheckInterval = 30;
        private boolean autoReconnect = true;
    }

    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /**
     * Circuit Breaker configuration for database protection.
     *
     * <p>The Circuit Breaker protects your application from cascading failures
     * when SurrealDB is unavailable or experiencing issues.
     *
     * <p>States:
     * <ul>
     *   <li><strong>CLOSED</strong> - Normal operation, requests pass through</li>
     *   <li><strong>OPEN</strong> - Too many failures, requests fail fast</li>
     *   <li><strong>HALF_OPEN</strong> - Testing if service is recovered</li>
     * </ul>
     *
     * <p>Example configuration:
     * <pre>
     * oneiros:
     *   circuit-breaker:
     *     enabled: true
     *     failure-rate-threshold: 50
     *     wait-duration-in-open-state: 5
     *     permitted-calls-in-half-open-state: 3
     *     sliding-window-size: 10
     *     minimum-number-of-calls: 5
     * </pre>
     */
    @Setter
    @Getter
    public static class CircuitBreakerConfig {
        /**
         * Enable or disable the circuit breaker.
         * Default: true
         */
        private boolean enabled = true;

        /**
         * Failure rate threshold in percentage.
         * When this percentage of calls fail, the circuit opens.
         * Default: 50 (50%)
         */
        private float failureRateThreshold = 50;

        /**
         * Duration in seconds the circuit stays open before transitioning to half-open.
         * Default: 5 seconds
         */
        private long waitDurationInOpenState = 5;

        /**
         * Number of permitted calls in half-open state to test if the service recovered.
         * Default: 3
         */
        private int permittedCallsInHalfOpenState = 3;

        /**
         * Size of the sliding window used to record call outcomes.
         * Default: 10
         */
        private int slidingWindowSize = 10;

        /**
         * Minimum number of calls required before the circuit breaker can calculate the failure rate.
         * Default: 5
         */
        private int minimumNumberOfCalls = 5;

        /**
         * Slow call duration threshold in milliseconds.
         * Calls taking longer than this are considered slow.
         * Default: 2000 (2 seconds)
         */
        private long slowCallDurationThreshold = 2000;

        /**
         * Slow call rate threshold in percentage.
         * When this percentage of calls are slow, it contributes to opening the circuit.
         * Default: 100 (disabled by default)
         */
        private float slowCallRateThreshold = 100;
    }
}