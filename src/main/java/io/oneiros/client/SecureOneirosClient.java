package io.oneiros.client;

import io.oneiros.security.OneirosSecurityHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Secure wrapper around {@link OneirosClient} that provides transparent encryption/decryption
 * of fields annotated with {@link io.oneiros.annotation.OneirosEncrypted}.
 *
 * <p>This client automatically:
 * <ul>
 *   <li><b>Encrypts</b> data before writing to database (create, update, merge, insert, upsert)</li>
 *   <li><b>Decrypts</b> data after reading from database (select, query)</li>
 * </ul>
 *
 * <p><b>Transparent Encryption Flow:</b>
 * <pre>
 * User Code                    SecureOneirosClient              Database
 * ─────────────────────────────────────────────────────────────────────
 * user.password = "plain"
 *    │
 *    ├─ create(user) ──────▶  encryptOnWrite(user)
 *    │                            │
 *    │                            └─ user.password = "AES:..."
 *    │                                    │
 *    │                                    └──▶ RPC create ─────▶ [encrypted]
 *    │
 *    │◀──────────────────────  decryptOnRead(result) ◀──────── [encrypted]
 *    │                                    │
 * user.password = "plain"  ◀──────────────┘
 * </pre>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * OneirosSecurityHandler securityHandler = new OneirosSecurityHandler(...);
 * OneirosClient secureClient = new SecureOneirosClient(regularClient, securityHandler);
 *
 * // Transparent encryption
 * User user = new User();
 * user.setPassword("secret");  // Plain text
 *
 * User created = secureClient.create("users", user, User.class).block();
 * // Database has: "AES:encrypted..."
 * // created.getPassword() returns: "secret" (decrypted)
 * }</pre>
 *
 * @see OneirosSecurityHandler
 * @see io.oneiros.annotation.OneirosEncrypted
 */
public class SecureOneirosClient implements OneirosClient {

    private final OneirosClient delegate;
    private final OneirosSecurityHandler securityHandler;

    public SecureOneirosClient(OneirosClient delegate, OneirosSecurityHandler securityHandler) {
        this.delegate = delegate;
        this.securityHandler = securityHandler;
    }

    // ============================================================
    // CONNECTION & SESSION (pass-through, no encryption needed)
    // ============================================================

    @Override
    public Mono<Void> connect() {
        return delegate.connect();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public Mono<Void> disconnect() {
        return delegate.disconnect();
    }

    @Override
    public Mono<Void> authenticate(String token) {
        return delegate.authenticate(token);
    }

    @Override
    public Mono<String> signin(Map<String, Object> credentials) {
        return delegate.signin(credentials);
    }

    @Override
    public Mono<String> signup(Map<String, Object> credentials) {
        return delegate.signup(credentials);
    }

    @Override
    public Mono<Void> invalidate() {
        return delegate.invalidate();
    }

    @Override
    public <T> Mono<T> info(Class<T> resultType) {
        return delegate.info(resultType)
            .map(securityHandler::decryptOnRead);
    }

    @Override
    public Mono<Void> reset() {
        return delegate.reset();
    }

    @Override
    public Mono<Void> ping() {
        return delegate.ping();
    }

    @Override
    public Mono<Map<String, Object>> version() {
        return delegate.version();
    }

    @Override
    public Mono<Void> use(String namespace, String database) {
        return delegate.use(namespace, database);
    }

    @Override
    public Mono<Void> let(String name, Object value) {
        return delegate.let(name, value);
    }

    @Override
    public Mono<Void> unset(String name) {
        return delegate.unset(name);
    }

    // ============================================================
    // QUERIES (decrypt results)
    // ============================================================

    @Override
    public <T> Flux<T> query(String sql, Class<T> resultType) {
        return delegate.query(sql, resultType)
            .map(securityHandler::decryptOnRead);
    }

    @Override
    public <T> Flux<T> query(String sql, Map<String, Object> params, Class<T> resultType) {
        return delegate.query(sql, params, resultType)
            .map(securityHandler::decryptOnRead);
    }

    @Override
    public Mono<Map<String, Object>> graphql(Object query, Map<String, Object> options) {
        return delegate.graphql(query, options);
    }

    @Override
    public <T> Mono<T> run(String functionName, String version, List<Object> args, Class<T> resultType) {
        return delegate.run(functionName, version, args, resultType)
            .map(securityHandler::decryptOnRead);
    }

    // ============================================================
    // CRUD OPERATIONS (encrypt writes, decrypt reads)
    // ============================================================

    @Override
    public <T> Flux<T> select(String thing, Class<T> resultType) {
        return delegate.select(thing, resultType)
            .map(securityHandler::decryptOnRead);
    }

    @Override
    public <T> Flux<T> select(String thing, Map<String, Object> options, Class<T> resultType) {
        return delegate.select(thing, options, resultType)
            .map(securityHandler::decryptOnRead);
    }

    @Override
    public <T> Mono<T> create(String thing, Object data, Class<T> resultType) {
        Object encrypted = securityHandler.encryptOnWrite(data);
        return delegate.create(thing, encrypted, resultType)
            .map(securityHandler::decryptOnRead);
    }

    @Override
    public <T> Flux<T> insert(String thing, Object data, Class<T> resultType) {
        Object encrypted = securityHandler.encryptOnWrite(data);
        return delegate.insert(thing, encrypted, resultType)
            .map(securityHandler::decryptOnRead);
    }

    @Override
    public <T> Flux<T> update(String thing, Object data, Class<T> resultType) {
        Object encrypted = securityHandler.encryptOnWrite(data);
        return delegate.update(thing, encrypted, resultType)
            .map(securityHandler::decryptOnRead);
    }

    @Override
    public <T> Flux<T> upsert(String thing, Object data, Class<T> resultType) {
        Object encrypted = securityHandler.encryptOnWrite(data);
        return delegate.upsert(thing, encrypted, resultType)
            .map(securityHandler::decryptOnRead);
    }

    @Override
    public <T> Flux<T> merge(String thing, Object data, Class<T> resultType) {
        Object encrypted = securityHandler.encryptOnWrite(data);
        return delegate.merge(thing, encrypted, resultType)
            .map(securityHandler::decryptOnRead);
    }

    @Override
    public Flux<Map<String, Object>> patch(String thing, List<Map<String, Object>> patches, boolean returnDiff) {
        // Patch operations are raw JSON patches, no object encryption needed
        return delegate.patch(thing, patches, returnDiff);
    }

    @Override
    public <T> Flux<T> delete(String thing, Class<T> resultType) {
        return delegate.delete(thing, resultType)
            .map(securityHandler::decryptOnRead);
    }

    // ============================================================
    // GRAPH RELATIONS (encrypt data, decrypt results)
    // ============================================================

    @Override
    public <T> Mono<T> relate(String in, String relation, String out, Object data, Class<T> resultType) {
        Object encrypted = data != null ? securityHandler.encryptOnWrite(data) : null;
        return delegate.relate(in, relation, out, encrypted, resultType)
            .map(securityHandler::decryptOnRead);
    }

    @Override
    public <T> Mono<T> insertRelation(String table, Object data, Class<T> resultType) {
        Object encrypted = securityHandler.encryptOnWrite(data);
        return delegate.insertRelation(table, encrypted, resultType)
            .map(securityHandler::decryptOnRead);
    }

    // ============================================================
    // LIVE QUERIES (decrypt results)
    // ============================================================

    @Override
    public Mono<String> live(String table, boolean diff) {
        return delegate.live(table, diff);
    }

    @Override
    public Flux<Map<String, Object>> listenToLiveQuery(String liveQueryId) {
        return delegate.listenToLiveQuery(liveQueryId);
        // Note: Decryption for live queries is handled by OneirosLiveManager
    }

    @Override
    public Mono<Void> kill(String liveQueryId) {
        return delegate.kill(liveQueryId);
    }

    // ============================================================
    // TRANSACTIONS (pass-through with decryption)
    // ============================================================

    @Override
    public <T> Mono<T> transaction(java.util.function.Function<io.oneiros.transaction.OneirosTransaction, Mono<T>> transactionBlock) {
        return delegate.transaction(transactionBlock)
            .map(securityHandler::decryptOnRead);
    }


    @Override
    public <T> Flux<T> transactionMany(java.util.function.Function<io.oneiros.transaction.OneirosTransaction, Flux<T>> transactionBlock) {
        return delegate.transactionMany(transactionBlock)
            .map(securityHandler::decryptOnRead);
    }

    /**
     * Gets the underlying delegate client (for advanced use cases).
     */
    public OneirosClient getDelegate() {
        return delegate;
    }

    /**
     * Gets the security handler.
     */
    public OneirosSecurityHandler getSecurityHandler() {
        return securityHandler;
    }
}

