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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages LIVE SELECT subscriptions and distributes real-time events.
 * Handles automatic decryption of @OneirosEncrypted fields in live events.
 */
public class OneirosLiveManager {

    private static final Logger log = LoggerFactory.getLogger(OneirosLiveManager.class);

    private final OneirosClient client;
    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;

    private final Map<String, Sinks.Many<OneirosEvent<?>>> activeLiveQueries = new ConcurrentHashMap<>();

    public OneirosLiveManager(OneirosClient client, ObjectMapper objectMapper, CryptoService cryptoService) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
    }

    /**
     * Creates a LIVE SELECT subscription for the specified table.
     *
     * @param table The table name
     * @param entityClass The entity class
     * @param whereClause Optional WHERE clause (can be null)
     * @param <T> The entity type
     * @return Flux of real-time events
     */
    @SuppressWarnings("unchecked")
    public <T> Flux<OneirosEvent<T>> subscribe(String table, Class<T> entityClass, String whereClause) {
        String liveQueryId = UUID.randomUUID().toString();

        Sinks.Many<OneirosEvent<T>> sink = Sinks.many().multicast().onBackpressureBuffer();

        // Store with type erasure
        activeLiveQueries.put(liveQueryId, (Sinks.Many<OneirosEvent<?>>) (Object) sink);

        String sql = buildLiveSelectSql(table, whereClause);

        log.info("🔴 Starting LIVE SELECT: {} (ID: {})", sql, liveQueryId);

        return client.query(sql, Map.class)
            .next()
            .flatMapMany(response -> {
                String actualLiveQueryId = extractLiveQueryId(response);

                if (actualLiveQueryId != null) {
                    activeLiveQueries.remove(liveQueryId);
                    activeLiveQueries.put(actualLiveQueryId, (Sinks.Many<OneirosEvent<?>>) (Object) sink);

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
     */
    public Mono<Void> killLiveQuery(String liveQueryId) {
        log.info("⏹️ Killing LIVE SELECT: {}", liveQueryId);

        String sql = "KILL '" + liveQueryId + "'";

        return client.query(sql, Map.class)
            .then()
            .doOnSuccess(v -> {
                Sinks.Many<OneirosEvent<?>> sink = activeLiveQueries.remove(liveQueryId);
                if (sink != null) {
                    sink.tryEmitComplete();
                }
            });
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
            Sinks.Many<OneirosEvent<T>> sink
    ) {
        return client.listenToLiveQuery(liveQueryId)
            .flatMap(notification -> {
                try {
                    OneirosEvent.Action action = parseAction(notification);
                    T data = parseAndDecryptData(notification, entityClass);

                    OneirosEvent<T> event = new OneirosEvent<>(action, data, liveQueryId);
                    sink.tryEmitNext(event);

                    return Mono.just(event);
                } catch (Exception e) {
                    log.error("❌ Error processing live event: {}", e.getMessage());
                    OneirosEvent<T> errorEvent = new OneirosEvent<>(
                        OneirosEvent.Action.UPDATE,
                        null,
                        liveQueryId,
                        e
                    );
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
     * Parses the data from notification and automatically decrypts @OneirosEncrypted fields.
     */
    @SuppressWarnings("unchecked")
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
     */
    private <T> void decryptEncryptedFields(T entity) {
        if (entity == null || cryptoService == null) {
            return;
        }

        try {
            for (Field field : entity.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(io.oneiros.annotation.OneirosEncrypted.class)) {
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
     * Builds the LIVE SELECT SQL statement.
     */
    private String buildLiveSelectSql(String table, String whereClause) {
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
     * Fluent API entry point for creating LIVE SELECT subscriptions.
     *
     * @param entityClass The entity class to subscribe to
     * @param <T> The entity type
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
                io.oneiros.annotation.OneirosEntity annotation =
                    entityClass.getAnnotation(io.oneiros.annotation.OneirosEntity.class);
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

        public LiveSelectBuilder<T> where(String whereClause) {
            this.whereClause = whereClause;
            return this;
        }

        public Flux<OneirosEvent<T>> subscribe() {
            return manager.subscribe(table, entityClass, whereClause);
        }
    }
}
