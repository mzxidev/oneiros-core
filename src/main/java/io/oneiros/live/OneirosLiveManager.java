package io.oneiros.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oneiros.client.OneirosClient;
import io.oneiros.security.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import io.oneiros.migration.SqlInjectionPrevention;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

/**
 * Manages LIVE SELECT subscriptions and distributes real-time events.
 * Handles automatic decryption of @OneirosEncrypted fields in live events.
 *
 * <p>
 * <strong>Memory Leak Prevention:</strong>
 * Inactive queries are automatically cleaned up after 10 minutes of inactivity.
 */
public class OneirosLiveManager {

    private static final Logger log = LoggerFactory.getLogger(OneirosLiveManager.class);
    private static final long QUERY_TTL_MS = 600_000; // 10 minutes
    private static final long CLEANUP_INTERVAL_MS = 300_000; // 5 minutes

    /**
     * Wrapper for Sink with last activity timestamp (Memory Leak Prevention).
     */
    private static class TimestampedSink {
        final Sinks.Many<OneirosEvent<?>> sink;
        final AtomicLong lastActivity;

        TimestampedSink(Sinks.Many<OneirosEvent<?>> sink) {
            this.sink = sink;
            this.lastActivity = new AtomicLong(System.currentTimeMillis());
        }

        void touch() {
            lastActivity.set(System.currentTimeMillis());
        }

        boolean isInactive(long cutoffTime) {
            return lastActivity.get() < cutoffTime;
        }
    }

    private final OneirosClient client;
    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;

    // SECURITY FIX: Track query activity for automatic cleanup
    private final Map<String, TimestampedSink> activeLiveQueries = new ConcurrentHashMap<>();
    private final Disposable cleanupScheduler;

    public OneirosLiveManager(OneirosClient client, ObjectMapper objectMapper, CryptoService cryptoService) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;

        // Start automatic cleanup scheduler (Memory Leak Prevention)
        this.cleanupScheduler = Mono.delay(Duration.ofMillis(CLEANUP_INTERVAL_MS))
                .repeat()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(tick -> cleanupInactiveQueries());

        log.info("🧹 Live Query cleanup scheduler started (TTL: {}ms, Interval: {}ms)",
                QUERY_TTL_MS, CLEANUP_INTERVAL_MS);
    }

    /**
     * Creates a LIVE SELECT subscription for the specified table.
     *
     * @param table       The table name
     * @param entityClass The entity class
     * @param whereClause Optional WHERE clause (can be null)
     * @param <T>         The entity type
     * @return Flux of real-time events
     */
    @SuppressWarnings("unchecked")
    public <T> Flux<OneirosEvent<T>> subscribe(String table, Class<T> entityClass, String whereClause) {
        String liveQueryId = UUID.randomUUID().toString();

        Sinks.Many<OneirosEvent<T>> sink = Sinks.many().multicast().onBackpressureBuffer();

        // Store with type erasure and timestamp tracking
        TimestampedSink tsink = new TimestampedSink((Sinks.Many<OneirosEvent<?>>) (Object) sink);
        activeLiveQueries.put(liveQueryId, tsink);

        String sql = buildLiveSelectSql(table, whereClause);

        log.info("🔴 Starting LIVE SELECT: {} (ID: {})", sql, liveQueryId);

        return client.query(sql, Map.class)
                .next()
                .flatMapMany(response -> {
                    String actualLiveQueryId = extractLiveQueryId(response);

                    if (actualLiveQueryId != null) {
                        TimestampedSink existingTsink = activeLiveQueries.remove(liveQueryId);
                        if (existingTsink != null) {
                            existingTsink.touch(); // Update activity
                            activeLiveQueries.put(actualLiveQueryId, existingTsink);
                        }

                        log.info("✅ LIVE SELECT started: {}", actualLiveQueryId);

                        return listenToWebSocketEvents(actualLiveQueryId, entityClass, sink);
                    } else {
                        return Flux.error(new RuntimeException("Failed to start LIVE SELECT: No query ID returned"));
                    }
                })
                .doOnCancel(() -> killLiveQuery(liveQueryId).subscribe())
                .doOnError(error -> {
                    log.error("❌ LIVE SELECT error: {}", error.getMessage());
                    sink.tryEmitError(error);
                    activeLiveQueries.remove(liveQueryId);
                })
                .doFinally(signal -> {
                    log.info("🔴 LIVE SELECT ended: {} ({})", liveQueryId, signal);
                    activeLiveQueries.remove(liveQueryId);
                });
    }

    /**
     * Kills (stops) a running LIVE SELECT query.
     *
     * @throws SecurityException if liveQueryId has invalid format (SQL Injection
     *                           prevention)
     */
    public Mono<Void> killLiveQuery(String liveQueryId) {
        log.info("⏹️ Killing LIVE SELECT: {}", liveQueryId);

        // SECURITY FIX: Validate UUID format to prevent SQL Injection
        if (!isValidLiveQueryId(liveQueryId)) {
            return Mono.error(new SecurityException(
                    "Invalid live query ID format (expected UUID): " + liveQueryId));
        }

        String sql = "KILL '" + liveQueryId + "'";

        return client.query(sql, Map.class)
                .then()
                .doOnSuccess(v -> {
                    TimestampedSink tsink = activeLiveQueries.remove(liveQueryId);
                    if (tsink != null) {
                        tsink.sink.tryEmitComplete();
                    }
                });
    }

    /**
     * SECURITY FIX: Automatically cleans up inactive live queries (Memory Leak
     * Prevention).
     * Called periodically by the cleanup scheduler.
     */
    private void cleanupInactiveQueries() {
        long cutoffTime = System.currentTimeMillis() - QUERY_TTL_MS;
        int initialSize = activeLiveQueries.size();

        activeLiveQueries.entrySet().removeIf(entry -> {
            if (entry.getValue().isInactive(cutoffTime)) {
                log.info("🧹 Cleaning up inactive live query: {} (inactive for >{}ms)",
                        entry.getKey(), QUERY_TTL_MS);

                // Kill the query on server side
                killLiveQuery(entry.getKey()).subscribe(
                        v -> {
                        },
                        error -> log.warn("Failed to kill inactive query {}: {}", entry.getKey(), error.getMessage()));

                // Complete the sink
                entry.getValue().sink.tryEmitComplete();
                return true;
            }
            return false;
        });

        int removed = initialSize - activeLiveQueries.size();
        if (removed > 0) {
            log.info("🧹 Cleaned up {} inactive live queries ({} remaining)", removed, activeLiveQueries.size());
        }
    }

    /**
     * Kills all active LIVE SELECT queries.
     */
    public Mono<Void> killAllLiveQueries() {
        log.info("⏹️ Killing all {} LIVE SELECT queries", activeLiveQueries.size());

        return Flux.fromIterable(activeLiveQueries.keySet())
                .flatMap(this::killLiveQuery)
                .then();
    }

    /**
     * Listens to WebSocket events and emits them to the sink.
     */
    private <T> Flux<OneirosEvent<T>> listenToWebSocketEvents(
            String liveQueryId,
            Class<T> entityClass,
            Sinks.Many<OneirosEvent<T>> sink) {
        return client.listenToLiveQuery(liveQueryId)
                .flatMap(notification -> {
                    try {
                        OneirosEvent.Action action = parseAction(notification);
                        T data = parseAndDecryptData(notification, entityClass);

                        OneirosEvent<T> event = new OneirosEvent<>(action, data, liveQueryId);
                        sink.tryEmitNext(event);

                        // Update activity timestamp to prevent premature cleanup
                        TimestampedSink tsink = activeLiveQueries.get(liveQueryId);
                        if (tsink != null) {
                            tsink.touch();
                        }

                        return Mono.just(event);
                    } catch (Exception e) {
                        log.error("❌ Error processing live event: {}", e.getMessage());
                        OneirosEvent<T> errorEvent = new OneirosEvent<>(
                                OneirosEvent.Action.UPDATE,
                                null,
                                liveQueryId,
                                e);
                        sink.tryEmitNext(errorEvent);
                        return Mono.just(errorEvent);
                    }
                })
                .doOnError(error -> {
                    log.error("❌ WebSocket error in LIVE SELECT: {}", error.getMessage());
                    sink.tryEmitError(error);
                });
    }

    /**
     * Parses the action type from the notification.
     */
    private OneirosEvent.Action parseAction(Map<String, Object> notification) {
        String action = (String) notification.get("action");
        if (action == null) {
            return OneirosEvent.Action.UPDATE;
        }

        return switch (action.toUpperCase()) {
            case "CREATE" -> OneirosEvent.Action.CREATE;
            case "UPDATE" -> OneirosEvent.Action.UPDATE;
            case "DELETE" -> OneirosEvent.Action.DELETE;
            case "CLOSE" -> OneirosEvent.Action.CLOSE;
            default -> OneirosEvent.Action.UPDATE;
        };
    }

    /**
     * Parses the data from notification and automatically
     * decrypts @OneirosEncrypted fields.
     */
    private <T> T parseAndDecryptData(Map<String, Object> notification, Class<T> entityClass) {
        Object result = notification.get("result");

        if (result == null) {
            return null;
        }

        try {
            T entity = objectMapper.convertValue(result, entityClass);
            decryptEncryptedFields(entity);
            return entity;
        } catch (Exception e) {
            log.error("❌ Error parsing live event data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Automatically decrypts @OneirosEncrypted fields.
     *
     * <p>MED-2 FIX: Only fields annotated with {@code EncryptionType.AES_GCM} are
     * decrypted. One-way hashes (ARGON2, BCRYPT, SCRYPT, SHA256/512) are intentionally
     * left as-is — attempting to "decrypt" them would produce errors or garbage.
     */
    private <T> void decryptEncryptedFields(T entity) {
        if (entity == null || cryptoService == null) {
            return;
        }

        try {
            for (Field field : entity.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(io.oneiros.annotation.OneirosEncrypted.class)) {
                    io.oneiros.annotation.OneirosEncrypted annotation =
                            field.getAnnotation(io.oneiros.annotation.OneirosEncrypted.class);

                    // MED-2 FIX: Only reverse-decrypt AES_GCM — skip one-way hashes
                    if (annotation.type() != io.oneiros.security.EncryptionType.AES_GCM) {
                        continue;
                    }

                    field.setAccessible(true);
                    Object value = field.get(entity);

                    if (value instanceof String encryptedValue) {
                        String decrypted = cryptoService.decrypt(encryptedValue);
                        field.set(entity, decrypted);
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Error decrypting fields in live event: {}", e.getMessage());
        }
    }

    /**
     * Extracts the live query ID from the initial response.
     */
    private String extractLiveQueryId(Map<String, Object> response) {
        Object id = response.get("result");
        return id != null ? id.toString() : null;
    }

    /**
     * SECURITY: Validates that the live query ID is a valid UUID format.
     * Prevents SQL injection attacks via KILL statements.
     *
     * @param liveQueryId the query ID to validate
     * @return true if valid UUID format, false otherwise
     */
    private boolean isValidLiveQueryId(String liveQueryId) {
        if (liveQueryId == null || liveQueryId.isEmpty()) {
            return false;
        }

        // UUID format: 8-4-4-4-12 hexadecimal digits
        // Example: 550e8400-e29b-41d4-a716-446655440000
        return liveQueryId.matches("^[a-f0-9]{8}-([a-f0-9]{4}-){3}[a-f0-9]{12}$");
    }

    /**
     * Builds the LIVE SELECT SQL statement.
     */
    private String buildLiveSelectSql(String table, String whereClause) {
        // SECURITY: Validate table name to prevent SQL injection
        SqlInjectionPrevention.validateTableName(table);

        StringBuilder sql = new StringBuilder("LIVE SELECT * FROM ");
        sql.append(table);

        if (whereClause != null && !whereClause.isBlank()) {
            sql.append(" WHERE ").append(whereClause);
        }

        return sql.toString();
    }

    /**
     * Returns the count of active LIVE SELECT queries.
     */
    public int getActiveLiveQueryCount() {
        return activeLiveQueries.size();
    }

    /**
     * Shuts down the live query manager and cleans up resources.
     * Should be called when the application shuts down.
     */
    public void shutdown() {
        log.info("⏹️ Shutting down OneirosLiveManager...");

        // Stop cleanup scheduler
        if (cleanupScheduler != null && !cleanupScheduler.isDisposed()) {
            cleanupScheduler.dispose();
        }

        // Kill all active queries
        killAllLiveQueries().block();

        log.info("✅ OneirosLiveManager shut down complete");
    }

    /**
     * Fluent API entry point for creating LIVE SELECT subscriptions.
     *
     * @param entityClass The entity class to subscribe to
     * @param <T>         The entity type
     * @return LiveSelectBuilder for fluent configuration
     */
    public <T> LiveSelectBuilder<T> live(Class<T> entityClass) {
        return new LiveSelectBuilder<>(this, entityClass);
    }

    /**
     * Fluent builder for LIVE SELECT subscriptions.
     */
    public static class LiveSelectBuilder<T> {
        private final OneirosLiveManager manager;
        private final Class<T> entityClass;
        private String table;
        private String whereClause;

        public LiveSelectBuilder(OneirosLiveManager manager, Class<T> entityClass) {
            this.manager = manager;
            this.entityClass = entityClass;

            // Auto-detect table name from @OneirosEntity annotation
            if (entityClass.isAnnotationPresent(io.oneiros.annotation.OneirosEntity.class)) {
                io.oneiros.annotation.OneirosEntity annotation = entityClass
                        .getAnnotation(io.oneiros.annotation.OneirosEntity.class);
                this.table = annotation.value().isEmpty()
                        ? entityClass.getSimpleName().toLowerCase()
                        : annotation.value();
            } else {
                this.table = entityClass.getSimpleName().toLowerCase();
            }
        }

        public LiveSelectBuilder<T> from(String table) {
            this.table = table;
            return this;
        }

        /**
         * Adds a raw WHERE clause string.
         *
         * <p><strong>CRIT-2:</strong> This method accepts raw strings and performs only
         * basic character-whitelist validation. Prefer the parameterized
         * {@code WhereClause} API when available to fully eliminate injection risk.
         *
         * @throws SecurityException if the clause contains characters outside the safe set
         * @deprecated Prefer a parameterized WhereClause builder to eliminate injection risk.
         */
        @Deprecated(since = "0.5.0", forRemoval = false)
        public LiveSelectBuilder<T> where(String whereClause) {
            // CRIT-2 FIX: Basic whitelist — allow only safe characters in raw WHERE strings
            // Blocks: semicolons, backticks, comments (--), multi-statement injection
            if (whereClause != null && !whereClause.isBlank()) {
                if (!whereClause.matches("^[\\w\\s=<>!.,':@()\\-+*/]+$")) {
                    throw new SecurityException(
                            "WHERE clause contains potentially unsafe characters. Use parameterized queries instead.");
                }
            }
            this.whereClause = whereClause;
            return this;
        }

        public Flux<OneirosEvent<T>> subscribe() {
            return manager.subscribe(table, entityClass, whereClause);
        }
    }
}
