package io.oneiros.transaction;

import io.oneiros.client.OneirosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Internal implementation of OneirosTransaction.
 *
 * <p>This class wraps an OneirosClient and ensures all operations are executed
 * on the same connection within an active transaction boundary.
 *
 * <p><strong>Important:</strong> Do not create instances directly. Use
 * {@link OneirosClient#transaction(java.util.function.Function)} instead.
 */
class OneirosTransactionImpl implements OneirosTransaction {

    private static final Logger log = LoggerFactory.getLogger(OneirosTransactionImpl.class);

    private final OneirosClient dedicatedClient;

    /**
     * Creates a transaction wrapper around a dedicated client connection.
     *
     * @param dedicatedClient A single, dedicated OneirosClient connection
     */
    OneirosTransactionImpl(OneirosClient dedicatedClient) {
        this.dedicatedClient = dedicatedClient;
        log.debug("🔄 Transaction context created");
    }

    // ============================================================
    // QUERIES
    // ============================================================

    @Override
    public <T> Flux<T> query(String sql, Class<T> resultType) {
        log.debug("📝 TX Query: {}", sql);
        return dedicatedClient.query(sql, resultType);
    }

    @Override
    public <T> Flux<T> query(String sql, Map<String, Object> params, Class<T> resultType) {
        log.debug("📝 TX Query with params: {}", sql);
        return dedicatedClient.query(sql, params, resultType);
    }

    // ============================================================
    // CRUD OPERATIONS
    // ============================================================

    @Override
    public <T> Flux<T> select(String thing, Class<T> resultType) {
        log.debug("📝 TX Select: {}", thing);
        return dedicatedClient.select(thing, resultType);
    }

    @Override
    public <T> Mono<T> create(String thing, Object data, Class<T> resultType) {
        log.debug("📝 TX Create: {}", thing);
        return dedicatedClient.create(thing, data, resultType);
    }

    @Override
    public <T> Flux<T> insert(String thing, Object data, Class<T> resultType) {
        log.debug("📝 TX Insert: {}", thing);
        return dedicatedClient.insert(thing, data, resultType);
    }

    @Override
    public <T> Flux<T> update(String thing, Object data, Class<T> resultType) {
        log.debug("📝 TX Update: {}", thing);
        return dedicatedClient.update(thing, data, resultType);
    }

    @Override
    public <T> Flux<T> upsert(String thing, Object data, Class<T> resultType) {
        log.debug("📝 TX Upsert: {}", thing);
        return dedicatedClient.upsert(thing, data, resultType);
    }

    @Override
    public <T> Flux<T> merge(String thing, Object data, Class<T> resultType) {
        log.debug("📝 TX Merge: {}", thing);
        return dedicatedClient.merge(thing, data, resultType);
    }

    @Override
    public Flux<Map<String, Object>> patch(String thing, List<Map<String, Object>> patches, boolean returnDiff) {
        log.debug("📝 TX Patch: {}", thing);
        return dedicatedClient.patch(thing, patches, returnDiff);
    }

    @Override
    public <T> Flux<T> delete(String thing, Class<T> resultType) {
        log.debug("📝 TX Delete: {}", thing);
        return dedicatedClient.delete(thing, resultType);
    }

    // ============================================================
    // GRAPH RELATIONS
    // ============================================================

    @Override
    public <T> Mono<T> relate(String in, String relation, String out, Object data, Class<T> resultType) {
        log.debug("📝 TX Relate: {} -[{}]-> {}", in, relation, out);
        return dedicatedClient.relate(in, relation, out, data, resultType);
    }

    @Override
    public <T> Mono<T> insertRelation(String table, Object data, Class<T> resultType) {
        log.debug("📝 TX Insert Relation: {}", table);
        return dedicatedClient.insertRelation(table, data, resultType);
    }

    // ============================================================
    // SESSION VARIABLES
    // ============================================================

    @Override
    public Mono<Void> let(String name, Object value) {
        log.debug("📝 TX Let: ${}={}", name, value);
        return dedicatedClient.let(name, value);
    }

    @Override
    public Mono<Void> unset(String name) {
        log.debug("📝 TX Unset: ${}", name);
        return dedicatedClient.unset(name);
    }
}

