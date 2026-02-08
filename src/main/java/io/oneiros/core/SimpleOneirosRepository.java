package io.oneiros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.oneiros.annotation.OneirosEncrypted;
import io.oneiros.annotation.OneirosEntity;
import io.oneiros.annotation.OneirosID;
import io.oneiros.client.OneirosClient;
import io.oneiros.config.OneirosProperties;
import io.oneiros.security.CryptoService;
import io.oneiros.security.EncryptionType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.time.Duration;

@Slf4j
public abstract class SimpleOneirosRepository<T, ID> implements ReactiveOneirosRepository<T, ID> {

    protected final OneirosClient client;
    protected final ObjectMapper mapper;
    protected final CryptoService crypto;

    private final Cache<String, T> localCache;
    private final boolean cacheEnabled;

    private final Class<T> entityType;
    private final String tableName;

    private final IdGenerator idGenerator;

    @SuppressWarnings("unchecked")
    public SimpleOneirosRepository(OneirosClient client, ObjectMapper mapper, CryptoService crypto, OneirosProperties properties) {
        this.client = client;
        this.mapper = mapper;
        this.crypto = crypto;
        this.idGenerator = new OneIDGenerator();

        // Cache Setup
        this.cacheEnabled = properties.getCache().isEnabled();
        if (this.cacheEnabled) {
            this.localCache = Caffeine.newBuilder()
                    .maximumSize(properties.getCache().getMaxSize())
                    .expireAfterWrite(Duration.ofSeconds(properties.getCache().getTtlSeconds()))
                    .recordStats() // Erlaubt uns später Statistiken abzurufen
                    .build();
        } else {
            this.localCache = null;
        }

        this.entityType = (Class<T>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];

        if (entityType.isAnnotationPresent(OneirosEntity.class)) {
            String value = entityType.getAnnotation(OneirosEntity.class).value();
            this.tableName = value.isEmpty() ? entityType.getSimpleName().toLowerCase() : value;
        } else {
            throw new IllegalArgumentException("Missing @OneirosEntity on " + entityType.getSimpleName());
        }
    }

    /**
     * Konstruktor ohne OneirosProperties - Cache ist deaktiviert
     */
    @SuppressWarnings("unchecked")
    public SimpleOneirosRepository(OneirosClient client, ObjectMapper mapper, CryptoService crypto) {
        this.client = client;
        this.mapper = mapper;
        this.crypto = crypto;
        this.idGenerator = new OneIDGenerator();

        // Cache deaktiviert
        this.cacheEnabled = false;
        this.localCache = null;

        this.entityType = (Class<T>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];

        if (entityType.isAnnotationPresent(OneirosEntity.class)) {
            String value = entityType.getAnnotation(OneirosEntity.class).value();
            this.tableName = value.isEmpty() ? entityType.getSimpleName().toLowerCase() : value;
        } else {
            throw new IllegalArgumentException("Missing @OneirosEntity on " + entityType.getSimpleName());
        }
    }

    public Mono<T> save(T entity) {
        return Mono.fromCallable(() -> {
            // 1. ID Check & Generierung
            Field idField = getIdField();
            idField.setAccessible(true);
            Object idVal = idField.get(entity);

            // Wenn ID fehlt -> Generiere neue (Cronos Style)
            if (idVal == null) {
                String newId = tableName + ":" + idGenerator.generate();
                idField.set(entity, newId);
                idVal = newId;
            } else {
                // Wenn ID da ist, aber kein "table:" Prefix hat
                if (!idVal.toString().contains(":")) {
                    idVal = tableName + ":" + idVal;
                    idField.set(entity, idVal); // Korrigierte ID zurück ins Objekt
                }
            }

            // 2. Encrypt IN PLACE (Daten für DB verschlüsseln)
            processEncryption(entity, true);
            return idVal.toString();
        }).flatMap(id -> {
            try {
                // Serialize entity to JSON
                String fullJson = mapper.writeValueAsString(entity);

                // FIX #1: Remove "id" field from JSON to avoid conflict
                // SurrealDB expects the ID not in CONTENT when it's already in the statement
                ObjectNode jsonNode = (ObjectNode) mapper.readTree(fullJson);
                jsonNode.remove("id");
                String jsonContent = jsonNode.toString();

                // FIX #2: Convert ISO-8601 datetime strings to SurrealDB d"..." literals
                // This is necessary because writeRawValue doesn't survive the readTree/toString roundtrip
                jsonContent = convertDatetimesToSurrealFormat(jsonContent);

                // "UPSERT user:123 CONTENT { ... } RETURN AFTER"
                String sql = "UPSERT " + id + " CONTENT " + jsonContent + " RETURN AFTER";

                // 3. Decrypt IN PLACE (Objekt für Java wieder lesbar machen)
                processEncryption(entity, false);

                return client.query(sql, entityType)
                        .next() // Nimm das erste Ergebnis
                        .map(saved -> {
                            processEncryption(saved, false); // Ergebnis entschlüsseln

                            // Cache aktualisieren
                            if (cacheEnabled && localCache != null) {
                                localCache.put(id, saved);
                                log.trace("🔄 Cache REFRESHED via UPSERT for {}", id);
                            }
                            return saved;
                        });
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    @Override
    public Mono<T> findById(ID id) {
        String dbId = id.toString();
        if (!dbId.contains(":")) dbId = tableName + ":" + dbId;
        final String lookupId = dbId;

        // Wenn Cache aus ist, direkt zur DB
        if (!cacheEnabled || localCache == null) {
            return fetchFromDb(lookupId);
        }

        // 1. Reactive Cache Lookup
        return Mono.justOrEmpty(localCache.getIfPresent(lookupId))
                .doOnNext(hit -> log.trace("🚀 Cache HIT for {}", lookupId))
                .switchIfEmpty(
                        // 2. Cache Miss -> DB Call -> Cache Put
                        fetchFromDb(lookupId)
                                .doOnNext(entity -> {
                                    log.trace("🐢 Cache MISS for {} - loading from DB", lookupId);
                                    localCache.put(lookupId, entity);
                                })
                );
    }

    private Mono<T> fetchFromDb(String dbId) {
        String sql = "SELECT * FROM " + dbId;
        return client.query(sql, entityType)
                .next()
                .map(found -> {
                    processEncryption(found, false); // Decrypt
                    return found;
                });
    }

    @Override
    public Flux<T> findAll() {
        String sql = "SELECT * FROM " + tableName;
        log.debug("📋 FindAll executing for table: {}", tableName);
        return client.query(sql, entityType)
                .map(found -> {
                    // Decrypten für jedes Element im Stream
                    processEncryption(found, false);
                    return found;
                });
    }

    @Override
    public Mono<Void> deleteById(ID id) {
        String dbId = id.toString();
        if (!dbId.contains(":")) dbId = tableName + ":" + dbId;

        // Cache Invalidation
        if (cacheEnabled && localCache != null) {
            localCache.invalidate(dbId);
            log.trace("🗑️ Cache EVICTED for {}", dbId);
        }

        return client.query("DELETE " + dbId, Object.class).then();
    }

    @Override
    public Flux<T> subscribe() {
        // Placeholder für Live Queries (folgt später)
        log.info("📡 Subscribing to live changes on table {}", tableName);
        return Flux.empty();
    }

    /**
     * Sucht das Feld in der Klasse, das mit @OneirosId annotiert ist.
     * Fallback: Sucht nach einem Feld namens "id".
     */
    private Field getIdField() {
        for (Field field : entityType.getDeclaredFields()) {
            if (field.isAnnotationPresent(OneirosID.class)) { // <--- Prüfung auf Annotation
                return field;
            }
        }
        // Fallback, falls Annotation vergessen wurde
        try {
            return entityType.getDeclaredField("id");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Entity " + entityType.getSimpleName() + " needs @OneirosId annotation");
        }
    }

    /**
     * Scannt alle Felder. Wenn @OneirosEncrypted gefunden wird -> Encrypt oder Decrypt.
     * Unterstützt verschiedene Algorithmen: AES_GCM, ARGON2, BCRYPT, SCRYPT, SHA256, SHA512.
     */
    private void processEncryption(T entity, boolean encrypt) {
        if (entity == null) return;

        for (Field field : entityType.getDeclaredFields()) {
            if (field.isAnnotationPresent(OneirosEncrypted.class)) {
                OneirosEncrypted annotation = field.getAnnotation(OneirosEncrypted.class);
                EncryptionType type = annotation.type();
                int strength = annotation.strength();

                field.setAccessible(true);
                try {
                    Object value = field.get(entity);
                    if (value instanceof String strValue) {
                        if (encrypt) {
                            // Encrypt/Hash the value
                            String processed = crypto.encrypt(strValue, type, strength);
                            field.set(entity, processed);
                        } else {
                            // Only decrypt for AES_GCM (reversible encryption)
                            // One-way hashes (ARGON2, BCRYPT, SCRYPT, SHA) cannot be decrypted
                            if (type == EncryptionType.AES_GCM) {
                                String processed = crypto.decrypt(strValue, type);
                                field.set(entity, processed);
                            }
                            // For password hashes, leave the hash as-is
                            // Use CryptoService.verify() to check passwords
                        }
                    }
                    // Info: Aktuell unterstützen wir nur String Encryption.
                    // Für Integer/andere müsste man sie erst zu String konvertieren.
                } catch (IllegalAccessException e) {
                    log.error("Crypto Access Error on field " + field.getName(), e);
                }
            }
        }
    }

    /**
     * Converts ISO-8601 datetime strings to SurrealDB datetime literals.
     *
     * <p>Example: "2024-01-01T00:00:00Z" becomes d"2024-01-01T00:00:00Z"
     *
     * <p>This is necessary because Jackson's writeRawValue() doesn't survive
     * the readTree/toString roundtrip that we use to remove the id field.
     *
     * <p>Matches patterns like:
     * <ul>
     *   <li>"2024-01-01T00:00:00Z"</li>
     *   <li>"2024-01-01T00:00:00.123Z"</li>
     *   <li>"2024-01-01T00:00:00.123456789Z"</li>
     * </ul>
     *
     * @param json The JSON string with ISO-8601 datetime strings
     * @return The JSON string with SurrealDB datetime literals
     */
    private String convertDatetimesToSurrealFormat(String json) {
        // Regex to match ISO-8601 datetime strings in JSON
        // Pattern: "YYYY-MM-DDTHH:MM:SS" with optional fractional seconds and timezone
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?)\""
        );

        java.util.regex.Matcher matcher = pattern.matcher(json);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String datetime = matcher.group(1);
            // Truncate nanoseconds to seconds for SurrealDB compatibility
            // Remove fractional seconds if present (everything after . and before Z or +/-)
            String truncated = datetime.replaceAll("\\.\\d+(?=Z|[+-]|$)", "");
            // Replace with SurrealDB datetime literal
            matcher.appendReplacement(sb, "d\"" + truncated + "\"");
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}
