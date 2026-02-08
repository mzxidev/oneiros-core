package io.oneiros.graph;

import io.oneiros.annotation.OneirosEncrypted;
import io.oneiros.client.OneirosClient;
import io.oneiros.security.CryptoService;
import io.oneiros.security.EncryptionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Fluent API for creating graph relations in SurrealDB.
 * Simplifies the RELATE statement with a builder pattern.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * oneiros.graph()
 *     .from("user:alice")
 *     .to("product:laptop")
 *     .via("purchased")
 *     .withData(Map.of("price", 999.99, "date", "2024-01-01"))
 *     .execute();
 * }
 * </pre>
 */
@Slf4j
public class OneirosGraph {

    private final OneirosClient client;
    private final ObjectMapper mapper;
    private final CryptoService crypto;

    private String fromRecord;
    private String toRecord;
    private String edgeTable;
    private Map<String, Object> edgeData;
    private boolean encryptionEnabled = true;
    private boolean returnBefore = false;
    private boolean returnAfter = true;
    private boolean returnDiff = false;
    private String timeout;

    public OneirosGraph(OneirosClient client, ObjectMapper mapper, CryptoService crypto) {
        this.client = client;
        this.mapper = mapper;
        this.crypto = crypto;
        this.edgeData = new HashMap<>();
    }

    /**
     * Set the source record (IN side of the relation).
     *
     * @param fromRecord Record ID (e.g., "user:alice" or just "alice" if table set)
     */
    public OneirosGraph from(String fromRecord) {
        this.fromRecord = fromRecord;
        return this;
    }

    /**
     * Set the source record from an entity object.
     * Extracts the @OneirosID field automatically.
     */
    public OneirosGraph from(Object entity) {
        this.fromRecord = extractId(entity);
        return this;
    }

    /**
     * Set the target record (OUT side of the relation).
     *
     * @param toRecord Record ID (e.g., "product:laptop")
     */
    public OneirosGraph to(String toRecord) {
        this.toRecord = toRecord;
        return this;
    }

    /**
     * Set the target record from an entity object.
     */
    public OneirosGraph to(Object entity) {
        this.toRecord = extractId(entity);
        return this;
    }

    /**
     * Set the edge table name (the relation type).
     *
     * @param edgeTable Name of the edge table (e.g., "purchased", "knows", "likes")
     */
    public OneirosGraph via(String edgeTable) {
        this.edgeTable = edgeTable;
        return this;
    }

    /**
     * Add data to the edge (relation properties).
     *
     * @param data Map of field names to values
     */
    public OneirosGraph withData(Map<String, Object> data) {
        this.edgeData.putAll(data);
        return this;
    }

    /**
     * Add a single field to the edge data.
     */
    public OneirosGraph with(String field, Object value) {
        this.edgeData.put(field, value);
        return this;
    }

    /**
     * Set data from an edge entity object.
     * Scans for @OneirosEncrypted fields if encryption is enabled.
     */
    public OneirosGraph withEntity(Object edgeEntity) {
        try {
            if (encryptionEnabled) {
                processEncryption(edgeEntity, true);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.convertValue(edgeEntity, Map.class);
            this.edgeData.putAll(map);

            if (encryptionEnabled) {
                processEncryption(edgeEntity, false);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert edge entity to map", e);
        }
        return this;
    }

    /**
     * Disable automatic encryption for edge data.
     * By default, @OneirosEncrypted fields are encrypted.
     */
    public OneirosGraph withoutEncryption() {
        this.encryptionEnabled = false;
        return this;
    }

    /**
     * Return the record BEFORE the relation was created.
     */
    public OneirosGraph returnBefore() {
        this.returnBefore = true;
        this.returnAfter = false;
        this.returnDiff = false;
        return this;
    }

    /**
     * Return the record AFTER the relation was created (default).
     */
    public OneirosGraph returnAfter() {
        this.returnBefore = false;
        this.returnAfter = true;
        this.returnDiff = false;
        return this;
    }

    /**
     * Return the diff of changes.
     */
    public OneirosGraph returnDiff() {
        this.returnBefore = false;
        this.returnAfter = false;
        this.returnDiff = true;
        return this;
    }

    /**
     * Set a timeout for the operation.
     *
     * @param timeout Duration string (e.g., "5s", "1m")
     */
    public OneirosGraph timeout(String timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Execute the RELATE statement and return the created edge.
     *
     * @param resultType Class to deserialize the result into
     * @return Mono containing the created edge record
     */
    public <T> Mono<T> execute(Class<T> resultType) {
        return Mono.fromCallable(() -> buildSql())
            .flatMap(sql -> {
                log.debug("🔗 Executing RELATE: {}", sql);
                return client.query(sql, resultType).next();
            });
    }

    /**
     * Execute the RELATE statement without returning a result.
     *
     * @return Mono<Void> indicating completion
     */
    public Mono<Void> execute() {
        return Mono.fromCallable(() -> buildSql())
            .flatMap(sql -> {
                log.debug("🔗 Executing RELATE: {}", sql);
                return client.query(sql, Object.class).then();
            });
    }

    /**
     * Build the SurrealQL RELATE statement.
     */
    public String toSql() {
        return buildSql();
    }

    private String buildSql() {
        if (fromRecord == null || fromRecord.isEmpty()) {
            throw new IllegalStateException("FROM record not set. Use .from()");
        }
        if (toRecord == null || toRecord.isEmpty()) {
            throw new IllegalStateException("TO record not set. Use .to()");
        }
        if (edgeTable == null || edgeTable.isEmpty()) {
            throw new IllegalStateException("Edge table not set. Use .via()");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("RELATE ");
        sql.append(fromRecord);
        sql.append("->");
        sql.append(edgeTable);
        sql.append("->");
        sql.append(toRecord);

        if (!edgeData.isEmpty()) {
            try {
                String json = mapper.writeValueAsString(edgeData);
                sql.append(" CONTENT ").append(json);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize edge data", e);
            }
        }

        if (returnBefore) {
            sql.append(" RETURN BEFORE");
        } else if (returnDiff) {
            sql.append(" RETURN DIFF");
        } else if (returnAfter) {
            sql.append(" RETURN AFTER");
        }

        if (timeout != null && !timeout.isEmpty()) {
            sql.append(" TIMEOUT ").append(timeout);
        }

        return sql.toString();
    }

    private String extractId(Object entity) {
        try {
            for (Field field : entity.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(io.oneiros.annotation.OneirosID.class)) {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    return value != null ? value.toString() : null;
                }
            }
            throw new IllegalArgumentException("No @OneirosID field found on " + entity.getClass());
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to extract ID from entity", e);
        }
    }

    private void processEncryption(Object entity, boolean encrypt) {
        try {
            for (Field field : entity.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(OneirosEncrypted.class)) {
                    OneirosEncrypted annotation = field.getAnnotation(OneirosEncrypted.class);
                    EncryptionType type = annotation.type();
                    int strength = annotation.strength();

                    field.setAccessible(true);
                    Object value = field.get(entity);

                    if (value instanceof String stringValue) {
                        if (encrypt) {
                            field.set(entity, crypto.encrypt(stringValue, type, strength));
                        } else {
                            // Only decrypt for AES_GCM (reversible encryption)
                            if (type == EncryptionType.AES_GCM) {
                                field.set(entity, crypto.decrypt(stringValue, type));
                            }
                            // Password hashes cannot be decrypted
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            log.error("Failed to process encryption on entity", e);
        }
    }
}
