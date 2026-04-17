package io.oneiros.sync;

import io.oneiros.antigravity.event.InternalEventBus;
import io.oneiros.antigravity.event.OneirosEvent;
import io.oneiros.client.OneirosClient;
import io.oneiros.transaction.OneirosTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * Hybrid client that supports Offline-First synchronization.
 * Automatically switches to local fallback when remote is unavailable.
 */
public class OneirosHybridClient implements OneirosClient {
    private static final Logger log = LoggerFactory.getLogger(OneirosHybridClient.class);

    private final OneirosClient remoteClient;
    private final OneirosClient localClient;
    private final Queue<SyncOperation> syncQueue = new ConcurrentLinkedQueue<>();
    private final InternalEventBus eventBus = InternalEventBus.getInstance();

    public OneirosHybridClient(OneirosClient remoteClient, OneirosClient localClient) {
        this.remoteClient = remoteClient;
        this.localClient = localClient;

        eventBus.listen(OneirosEvent.Connected.class)
                .subscribe(e -> synchronize());
    }

    private void synchronize() {
        if (syncQueue.isEmpty()) return;
        log.info("🔄 Starting synchronization of {} operations...", syncQueue.size());
        
        while (!syncQueue.isEmpty()) {
            SyncOperation op = syncQueue.poll();
            if (op == null) break;

            log.debug("📤 Replaying: {} on {}", op.method(), op.thing());
            replayOperation(op).subscribe(
                res -> log.debug("✅ Synced: {} on {}", op.method(), op.thing()),
                err -> log.error("💥 Sync failed for {}: {}", op.id(), err.getMessage())
            );
        }
    }

    private Mono<?> replayOperation(SyncOperation op) {
        return switch (op.method()) {
            case CREATE -> remoteClient.create(op.thing(), op.data(), Object.class);
            case UPDATE -> remoteClient.update(op.thing(), op.data(), Object.class).collectList();
            case DELETE -> remoteClient.delete(op.thing(), Object.class).collectList();
            default -> Mono.error(new UnsupportedOperationException("Method " + op.method() + " not syncable"));
        };
    }

    @Override
    public <T> Mono<T> create(String thing, Object data, Class<T> resultType) {
        if (remoteClient.isConnected()) {
            return remoteClient.create(thing, data, resultType);
        } else {
            syncQueue.add(new SyncOperation(UUID.randomUUID().toString(), SyncOperation.Method.CREATE, thing, data, Map.of(), Instant.now().toEpochMilli()));
            return localClient.create(thing, data, resultType);
        }
    }

    @Override
    public <T> Flux<T> update(String thing, Object data, Class<T> resultType) {
        if (remoteClient.isConnected()) {
            return remoteClient.update(thing, data, resultType);
        } else {
            syncQueue.add(new SyncOperation(UUID.randomUUID().toString(), SyncOperation.Method.UPDATE, thing, data, Map.of(), Instant.now().toEpochMilli()));
            return localClient.update(thing, data, resultType);
        }
    }

    @Override
    public <T> Flux<T> delete(String thing, Class<T> resultType) {
        if (remoteClient.isConnected()) {
            return remoteClient.delete(thing, resultType);
        } else {
            syncQueue.add(new SyncOperation(UUID.randomUUID().toString(), SyncOperation.Method.DELETE, thing, null, Map.of(), Instant.now().toEpochMilli()));
            return localClient.delete(thing, resultType);
        }
    }

    @Override
    public <T> Flux<T> select(String thing, Class<T> resultType) {
        if (remoteClient.isConnected()) return remoteClient.select(thing, resultType);
        return localClient.select(thing, resultType);
    }

    @Override
    public <T> Flux<T> select(String thing, Map<String, Object> options, Class<T> resultType) {
        if (remoteClient.isConnected()) return remoteClient.select(thing, options, resultType);
        return localClient.select(thing, options, resultType);
    }

    // --- Delegation to Remote (Standard methods) ---

    @Override public Mono<Void> connect() { return remoteClient.connect(); }
    @Override public boolean isConnected() { return remoteClient.isConnected(); }
    @Override public Mono<Void> disconnect() { return remoteClient.disconnect(); }
    @Override public Mono<Void> authenticate(String token) { return remoteClient.authenticate(token); }
    @Override public Mono<String> signin(Map<String, Object> credentials) { return remoteClient.signin(credentials); }
    @Override public Mono<String> signup(Map<String, Object> credentials) { return remoteClient.signup(credentials); }
    @Override public Mono<Void> invalidate() { return remoteClient.invalidate(); }
    @Override public <T> Mono<T> info(Class<T> resultType) { return remoteClient.info(resultType); }
    @Override public Mono<Void> reset() { return remoteClient.reset(); }
    @Override public Mono<Void> ping() { return remoteClient.ping(); }
    @Override public Mono<Map<String, Object>> version() { return remoteClient.version(); }
    @Override public Mono<Void> use(String ns, String db) { return remoteClient.use(ns, db); }
    @Override public Mono<Void> let(String n, Object v) { return remoteClient.let(n, v); }
    @Override public Mono<Void> unset(String n) { return remoteClient.unset(n); }
    @Override public <T> Flux<T> query(String s, Class<T> r) { return remoteClient.query(s, r); }
    @Override public <T> Flux<T> query(String s, Map<String, Object> p, Class<T> r) { return remoteClient.query(s, p, r); }
    @Override public Mono<Map<String, Object>> graphql(Object q, Map<String, Object> o) { return remoteClient.graphql(q, o); }
    @Override public <T> Mono<T> run(String f, String v, List<Object> a, Class<T> r) { return remoteClient.run(f, v, a, r); }
    @Override public <T> Flux<T> insert(String t, Object d, Class<T> r) { return remoteClient.insert(t, d, r); }
    @Override public <T> Flux<T> upsert(String t, Object d, Class<T> r) { return remoteClient.upsert(t, d, r); }
    @Override public <T> Flux<T> merge(String t, Object d, Class<T> r) { return remoteClient.merge(t, d, r); }
    @Override public Flux<Map<String, Object>> patch(String t, List<Map<String, Object>> p, boolean d) { return remoteClient.patch(t, p, d); }
    @Override public <T> Mono<T> relate(String i, String rl, String o, Object d, Class<T> r) { return remoteClient.relate(i, rl, o, d, r); }
    @Override public <T> Mono<T> insertRelation(String t, Object d, Class<T> r) { return remoteClient.insertRelation(t, d, r); }
    @Override public Mono<String> live(String t, boolean d) { return remoteClient.live(t, d); }
    @Override public Flux<Map<String, Object>> listenToLiveQuery(String i) { return remoteClient.listenToLiveQuery(i); }
    @Override public Mono<Void> kill(String i) { return remoteClient.kill(i); }
    @Override public <T> Mono<T> transaction(Function<OneirosTransaction, Mono<T>> b) { return remoteClient.transaction(b); }
    @Override public <T> Flux<T> transactionMany(Function<OneirosTransaction, Flux<T>> b) { return remoteClient.transactionMany(b); }
}
