package io.oneiros.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.oneiros.client.rpc.RpcRequest;
import io.oneiros.client.rpc.RpcResponse;
import io.oneiros.config.OneirosProperties;
import io.oneiros.core.OneirosConfig;
import io.oneiros.transaction.OneirosTransaction;
import io.oneiros.transaction.OneirosTransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Framework-agnostic reactive WebSocket client implementing the full SurrealDB RPC protocol.
 * Uses Reactor Netty directly (no Spring dependencies).
 *
 * <p>Provides non-blocking access to all SurrealDB operations including:
 * <ul>
 *   <li>Connection management (signin, use, reset)</li>
 *   <li>CRUD operations (select, create, update, delete, etc.)</li>
 *   <li>Graph relations (relate, insert_relation)</li>
 *   <li>Real-time queries (live, kill)</li>
 *   <li>Session management (let, unset, authenticate)</li>
 * </ul>
 *
 * <p>Works with both {@link OneirosConfig} (framework-agnostic) and
 * {@link OneirosProperties} (Spring Boot).
 */
public class OneirosWebsocketClient implements OneirosClient {

    private static final Logger log = LoggerFactory.getLogger(OneirosWebsocketClient.class);

    // Configuration - supports both types
    private final String url;
    private final String namespace;
    private final String database;
    private final String username;
    private final String password;

    private final ObjectMapper mapper;
    private final CircuitBreaker circuitBreaker;

    // Transaction support
    private final OneirosTransactionManager transactionManager;

    // The send sink for outgoing messages
    private final Sinks.Many<String> sendSink = Sinks.many().unicast().onBackpressureBuffer();
    private volatile boolean isConnecting = false;
    private volatile boolean isConnected = false;

    // Speicher für offene Anfragen: Request-ID -> Antwort-Kanal
    private final Map<String, Sinks.One<RpcResponse>> pendingRequests = new ConcurrentHashMap<>();

    // Live Query support: Live Query ID -> Event Sink
    private final Map<String, Sinks.Many<Map<String, Object>>> liveQuerySinks = new ConcurrentHashMap<>();

    // Signal for when connection initialization is complete (reset for each connection attempt)
    private volatile Sinks.One<Void> initCompleteSink = Sinks.one();

    /**
     * Creates a client from framework-agnostic OneirosConfig.
     */
    public OneirosWebsocketClient(OneirosConfig config, ObjectMapper mapper, CircuitBreaker circuitBreaker) {
        this.url = config.getUrl();
        this.namespace = config.getNamespace();
        this.database = config.getDatabase();
        this.username = config.getUsername();
        this.password = config.getPassword();
        this.mapper = mapper;
        this.circuitBreaker = circuitBreaker;
        this.transactionManager = new OneirosTransactionManager(this);
    }

    /**
     * Creates a client from Spring OneirosProperties (backward compatibility).
     */
    public OneirosWebsocketClient(OneirosProperties properties, ObjectMapper mapper, CircuitBreaker circuitBreaker) {
        this.url = properties.getUrl();
        this.namespace = properties.getNamespace();
        this.database = properties.getDatabase();
        this.username = properties.getUsername();
        this.password = properties.getPassword();
        this.mapper = mapper;
        this.circuitBreaker = circuitBreaker;
        this.transactionManager = new OneirosTransactionManager(this);
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public Mono<Void> connect() {
        if (isConnected) {
            return Mono.empty();
        }

        if (isConnecting) {
            // Already connecting, wait for init to complete
            return initCompleteSink.asMono()
                    .timeout(java.time.Duration.ofSeconds(15));
        }

        isConnecting = true;
        // Create a fresh sink for this connection attempt
        initCompleteSink = Sinks.one();
        URI uri = URI.create(this.url);

        // Use Reactor Netty directly (framework-agnostic)
        HttpClient httpClient = HttpClient.create();

        // Start the WebSocket connection in the background
        httpClient.websocket(WebsocketClientSpec.builder().build())
            .uri(uri)
            .handle((inbound, outbound) -> handleWebSocket(inbound, outbound, uri))
            .retry(3)
            .doOnError(e -> {
                log.error("💥 Connection fatal error: {}", e.getMessage());
                isConnecting = false;
                isConnected = false;
                initCompleteSink.tryEmitError(e);
            })
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .subscribe(
                v -> {},
                err -> log.error("❌ WebSocket connection error: {}", err.getMessage()),
                () -> {
                    log.debug("📡 WebSocket connection closed");
                    isConnected = false;
                }
            );

        // Wait for init to complete (signaled via initCompleteSink)
        return initCompleteSink.asMono()
                .timeout(java.time.Duration.ofSeconds(15))
                .doOnError(err -> log.error("⏱️ Connection timeout: {}", err.getMessage()));
    }

    /**
     * Handles the WebSocket connection using Reactor Netty native API.
     */
    private Mono<Void> handleWebSocket(WebsocketInbound inbound, WebsocketOutbound outbound, URI uri) {
        log.info("🌐 Oneiros connected to SurrealDB at {}", uri);

        // Mark as connected BEFORE running init, so RPC calls work
        isConnected = true;
        isConnecting = false;

        // The receive handler processes incoming messages
        Mono<Void> receiveHandler = inbound.receive()
            .asString()
            .doOnNext(this::handleIncomingMessage)
            .doOnError(err -> log.error("💥 Receive error: {}", err.getMessage()))
            .doOnComplete(() -> {
                log.debug("WebSocket receive completed");
                isConnected = false;
            })
            .then();

        // The send handler processes outgoing messages from sendSink
        Mono<Void> sendHandler = outbound
            .sendString(sendSink.asFlux())
            .then();

        // Authentication and namespace selection
        Mono<Void> init = rpc("signin", Map.of("user", this.username, "pass", this.password))
            .doOnSuccess(response -> log.debug("✅ Signin response received"))
            .doOnError(err -> log.error("❌ Signin failed: {}", err.getMessage()))
            .flatMap(signinResp -> {
                if (this.namespace != null && this.database != null) {
                    return rpc("use", this.namespace, this.database)
                        .doOnSuccess(response -> log.debug("✅ Use response received"))
                        .doOnError(err -> log.error("❌ Use failed: {}", err.getMessage()));
                } else {
                    return Mono.just(signinResp);
                }
            })
            .then()
            .timeout(java.time.Duration.ofSeconds(10))
            .doOnSuccess(v -> {
                log.debug("✅ Connection initialization complete");
                initCompleteSink.tryEmitValue(null);
            })
            .doOnError(err -> {
                log.error("💥 Init failed: {}", err.getMessage(), err);
                isConnecting = false;
                isConnected = false;
                initCompleteSink.tryEmitError(err);
            });

        // CRITICAL: receiveHandler, sendHandler and init MUST run in PARALLEL
        // - receiveHandler processes incoming WebSocket messages (including RPC responses)
        // - sendHandler sends outgoing messages from the sink
        // - init sends RPC requests (signin, use) and waits for responses
        // If they run sequentially, we get a deadlock:
        //   init waits for response -> response can't arrive because receiveHandler isn't running
        //
        // Using Flux.merge ensures all three start immediately and run concurrently.
        // The returned Mono completes when ALL complete (receiveHandler runs forever,
        // so the WebSocket stays open)
        return Flux.merge(
            receiveHandler.flux(),
            sendHandler.flux(),
            init.flux()
        ).then();
    }

    private void handleIncomingMessage(String json) {
        log.trace("📥 RX: {}", json);

        try {
            RpcResponse response = mapper.readValue(json, RpcResponse.class);

            if (response.id() != null) {
                Sinks.One<RpcResponse> sink = pendingRequests.remove(response.id());
                if (sink != null) {
                    if (response.error() != null) {
                        log.warn("⚠️ RPC error for {}: {}", response.id(), response.error());
                    }
                    sink.tryEmitValue(response);
                } else {
                    log.warn("⚠️ Received response for unknown ID: {}", response.id());
                }
            } else {
                // Check if this is a live query notification
                @SuppressWarnings("unchecked")
                Map<String, Object> notificationMap = mapper.readValue(json, Map.class);
                handleLiveQueryNotification(notificationMap);
            }
        } catch (Exception e) {
            log.error("💥 Failed to parse incoming message: {}", e.getMessage(), e);
            log.debug("💥 Raw message was: {}", json);
        }
    }

    private void handleLiveQueryNotification(Map<String, Object> notification) {
        String liveQueryId = extractLiveQueryId(notification);

        if (liveQueryId != null) {
            Sinks.Many<Map<String, Object>> sink = liveQuerySinks.get(liveQueryId);
            if (sink != null) {
                sink.tryEmitNext(notification);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String extractLiveQueryId(Map<String, Object> notification) {
        Object result = notification.get("result");
        if (result instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) result;
            Object id = resultMap.get("id");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    // ============================================================
    // CONNECTION & SESSION MANAGEMENT
    // ============================================================

    @Override
    public Mono<Void> authenticate(String token) {
        return rpc("authenticate", token)
                .then()
                .doOnSuccess(v -> log.debug("✅ Authenticated with token"))
                .doOnError(e -> log.error("❌ Authentication failed: {}", e.getMessage()));
    }

    @Override
    public Mono<String> signin(Map<String, Object> credentials) {
        return rpc("signin", credentials)
                .map(response -> {
                    if (response.result() == null) {
                        return null; // Root user signin returns null
                    }
                    return mapper.convertValue(response.result(), String.class);
                })
                .doOnSuccess(token -> log.debug("✅ Signed in, token: {}", token != null ? "present" : "null"))
                .doOnError(e -> log.error("❌ Signin failed: {}", e.getMessage()));
    }

    @Override
    public Mono<String> signup(Map<String, Object> credentials) {
        return rpc("signup", credentials)
                .map(response -> mapper.convertValue(response.result(), String.class))
                .doOnSuccess(token -> log.debug("✅ Signed up, token: {}", token))
                .doOnError(e -> log.error("❌ Signup failed: {}", e.getMessage()));
    }

    @Override
    public Mono<Void> invalidate() {
        return rpc("invalidate")
                .then()
                .doOnSuccess(v -> {
                    isConnected = false;
                    log.debug("✅ Session invalidated");
                });
    }

    @Override
    public <T> Mono<T> info(Class<T> resultType) {
        return rpc("info")
                .map(response -> mapper.convertValue(response.result(), resultType))
                .doOnSuccess(info -> log.debug("✅ Retrieved user info"))
                .doOnError(e -> log.error("❌ Failed to get user info: {}", e.getMessage()));
    }

    @Override
    public Mono<Void> reset() {
        return rpc("reset")
                .then()
                .doOnSuccess(v -> {
                    isConnected = false;
                    pendingRequests.clear();
                    liveQuerySinks.clear();
                    log.debug("✅ Session reset");
                });
    }

    @Override
    public Mono<Void> ping() {
        return rpc("ping")
                .then()
                .doOnSuccess(v -> log.trace("🏓 Ping successful"));
    }

    @Override
    public Mono<Map<String, Object>> version() {
        return rpc("version")
                .map(response -> mapper.convertValue(response.result(), new TypeReference<Map<String, Object>>() {}))
                .doOnSuccess(v -> log.debug("✅ Retrieved version: {}", v.get("version")));
    }

    @Override
    public Mono<Void> use(String namespace, String database) {
        return rpc("use", namespace, database)
                .then()
                .doOnSuccess(v -> log.debug("✅ Using namespace={}, database={}", namespace, database));
    }

    // ============================================================
    // VARIABLES
    // ============================================================

    @Override
    public Mono<Void> let(String name, Object value) {
        return rpc("let", name, value)
                .then()
                .doOnSuccess(v -> log.debug("✅ Variable ${} set", name))
                .doOnError(e -> log.error("❌ Failed to set variable ${}: {}", name, e.getMessage()));
    }

    @Override
    public Mono<Void> unset(String name) {
        return rpc("unset", name)
                .then()
                .doOnSuccess(v -> log.debug("✅ Variable ${} unset", name))
                .doOnError(e -> log.error("❌ Failed to unset variable ${}: {}", name, e.getMessage()));
    }

    // ============================================================
    // QUERIES
    // ============================================================

    @Override
    public <T> Flux<T> query(String sql, Class<T> resultType) {
        return query(sql, Map.of(), resultType);
    }

    @Override
    public <T> Flux<T> query(String sql, Map<String, Object> params, Class<T> resultType) {
        return rpc("query", sql, params)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .flatMapMany(response -> {
                    try {
                        // Check for RPC-level error first
                        if (response.error() != null) {
                            String errorMsg = response.error().toString();
                            return Flux.error(new RuntimeException("SurrealDB RPC Error: " + errorMsg));
                        }

                        List<Map<String, Object>> queryResults = mapper.convertValue(
                                response.result(), new TypeReference<List<Map<String, Object>>>() {}
                        );
                        if (queryResults == null || queryResults.isEmpty()) return Flux.empty();

                        Map<String, Object> firstSet = queryResults.getFirst();
                        String status = (String) firstSet.get("status");

                        if (!"OK".equals(status)) {
                            // Build detailed error message
                            Object detail = firstSet.get("detail");
                            Object result = firstSet.get("result");
                            String errorMessage;

                            if (detail != null) {
                                errorMessage = detail.toString();
                            } else if (result != null) {
                                errorMessage = result.toString();
                            } else {
                                errorMessage = "Query failed with status: " + status;
                            }

                            return Flux.error(new RuntimeException("SurrealDB Error: " + errorMessage));
                        }

                        Object rawResultData = firstSet.get("result");
                        if (rawResultData == null) return Flux.empty();

                        CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, resultType);
                        List<T> typedList = mapper.convertValue(rawResultData, listType);
                        return Flux.fromIterable(typedList);
                    } catch (Exception e) {
                        return Flux.error(e);
                    }
                })
                .doOnComplete(() -> log.debug("✅ Query completed"));
    }

    @Override
    public Mono<Map<String, Object>> graphql(Object query, Map<String, Object> options) {
        return rpc("graphql", query, options)
                .map(response -> mapper.convertValue(response.result(), new TypeReference<Map<String, Object>>() {}))
                .doOnSuccess(result -> log.debug("✅ GraphQL query executed"))
                .doOnError(e -> log.error("❌ GraphQL query failed: {}", e.getMessage()));
    }

    @Override
    public <T> Mono<T> run(String functionName, String version, List<Object> args, Class<T> resultType) {
        Object[] params = version != null && args != null
            ? new Object[]{functionName, version, args}
            : version != null
                ? new Object[]{functionName, version}
                : args != null
                    ? new Object[]{functionName, null, args}
                    : new Object[]{functionName};

        return rpc("run", params)
                .map(response -> mapper.convertValue(response.result(), resultType))
                .doOnSuccess(result -> log.debug("✅ Function {} executed", functionName))
                .doOnError(e -> log.error("❌ Function {} failed: {}", functionName, e.getMessage()));
    }

    // ============================================================
    // CRUD OPERATIONS
    // ============================================================

    @Override
    public <T> Flux<T> select(String thing, Class<T> resultType) {
        return select(thing, null, resultType);
    }

    @Override
    public <T> Flux<T> select(String thing, Map<String, Object> options, Class<T> resultType) {
        Object[] params = options != null ? new Object[]{thing, options} : new Object[]{thing};

        return rpc("select", params)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .flatMapMany(response -> {
                    try {
                        if (response.result() == null) {
                            return Flux.empty();
                        }

                        // Result can be a single object or an array
                        if (response.result() instanceof List) {
                            CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, resultType);
                            List<T> results = mapper.convertValue(response.result(), listType);
                            return Flux.fromIterable(results);
                        } else {
                            T singleResult = mapper.convertValue(response.result(), resultType);
                            return Flux.just(singleResult);
                        }
                    } catch (Exception e) {
                        return Flux.error(new RuntimeException("Failed to deserialize select result", e));
                    }
                })
                .doOnComplete(() -> log.debug("✅ Select from {} completed", thing));
    }

    @Override
    public <T> Mono<T> create(String thing, Object data, Class<T> resultType) {
        return rpc("create", thing, data)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .map(response -> {
                    // Create always returns an array with one element
                    if (response.result() instanceof List) {
                        List<?> list = (List<?>) response.result();
                        if (!list.isEmpty()) {
                            return mapper.convertValue(list.get(0), resultType);
                        }
                    }
                    return mapper.convertValue(response.result(), resultType);
                })
                .doOnSuccess(r -> log.debug("✅ Created record in {}", thing))
                .doOnError(e -> log.error("❌ Failed to create {}: {}", thing, e.getMessage()));
    }

    @Override
    public <T> Flux<T> insert(String thing, Object data, Class<T> resultType) {
        return rpc("insert", thing, data)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .flatMapMany(response -> {
                    try {
                        CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, resultType);
                        List<T> results = mapper.convertValue(response.result(), listType);
                        return Flux.fromIterable(results);
                    } catch (Exception e) {
                        return Flux.error(new RuntimeException("Failed to deserialize insert result", e));
                    }
                })
                .doOnComplete(() -> log.debug("✅ Inserted into {}", thing));
    }

    @Override
    public <T> Flux<T> update(String thing, Object data, Class<T> resultType) {
        return rpc("update", thing, data)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .flatMapMany(response -> {
                    try {
                        if (response.result() instanceof List) {
                            CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, resultType);
                            List<T> results = mapper.convertValue(response.result(), listType);
                            return Flux.fromIterable(results);
                        } else {
                            T singleResult = mapper.convertValue(response.result(), resultType);
                            return Flux.just(singleResult);
                        }
                    } catch (Exception e) {
                        return Flux.error(new RuntimeException("Failed to deserialize update result", e));
                    }
                })
                .doOnComplete(() -> log.debug("✅ Updated {}", thing));
    }

    @Override
    public <T> Flux<T> upsert(String thing, Object data, Class<T> resultType) {
        return rpc("upsert", thing, data)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .flatMapMany(response -> {
                    try {
                        if (response.result() instanceof List) {
                            CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, resultType);
                            List<T> results = mapper.convertValue(response.result(), listType);
                            return Flux.fromIterable(results);
                        } else {
                            T singleResult = mapper.convertValue(response.result(), resultType);
                            return Flux.just(singleResult);
                        }
                    } catch (Exception e) {
                        return Flux.error(new RuntimeException("Failed to deserialize upsert result", e));
                    }
                })
                .doOnComplete(() -> log.debug("✅ Upserted {}", thing));
    }

    @Override
    public <T> Flux<T> merge(String thing, Object data, Class<T> resultType) {
        return rpc("merge", thing, data)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .flatMapMany(response -> {
                    try {
                        if (response.result() instanceof List) {
                            CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, resultType);
                            List<T> results = mapper.convertValue(response.result(), listType);
                            return Flux.fromIterable(results);
                        } else {
                            T singleResult = mapper.convertValue(response.result(), resultType);
                            return Flux.just(singleResult);
                        }
                    } catch (Exception e) {
                        return Flux.error(new RuntimeException("Failed to deserialize merge result", e));
                    }
                })
                .doOnComplete(() -> log.debug("✅ Merged {}", thing));
    }

    @Override
    public Flux<Map<String, Object>> patch(String thing, List<Map<String, Object>> patches, boolean returnDiff) {
        return rpc("patch", thing, patches, returnDiff)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .flatMapMany(response -> {
                    try {
                        CollectionType listType = mapper.getTypeFactory().constructCollectionType(
                            List.class,
                            mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
                        );
                        List<Map<String, Object>> results = mapper.convertValue(response.result(), listType);
                        return Flux.fromIterable(results);
                    } catch (Exception e) {
                        return Flux.error(new RuntimeException("Failed to deserialize patch result", e));
                    }
                })
                .doOnComplete(() -> log.debug("✅ Patched {}", thing));
    }

    @Override
    public <T> Flux<T> delete(String thing, Class<T> resultType) {
        return rpc("delete", thing)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .flatMapMany(response -> {
                    try {
                        if (response.result() == null) {
                            return Flux.empty();
                        }

                        if (response.result() instanceof List) {
                            CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, resultType);
                            List<T> results = mapper.convertValue(response.result(), listType);
                            return Flux.fromIterable(results);
                        } else {
                            T singleResult = mapper.convertValue(response.result(), resultType);
                            return Flux.just(singleResult);
                        }
                    } catch (Exception e) {
                        return Flux.error(new RuntimeException("Failed to deserialize delete result", e));
                    }
                })
                .doOnComplete(() -> log.debug("✅ Deleted {}", thing));
    }

    // ============================================================
    // GRAPH RELATIONS
    // ============================================================

    @Override
    public <T> Mono<T> relate(String in, String relation, String out, Object data, Class<T> resultType) {
        Object[] params = data != null
            ? new Object[]{in, relation, out, data}
            : new Object[]{in, relation, out};

        return rpc("relate", params)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .map(response -> mapper.convertValue(response.result(), resultType))
                .doOnSuccess(r -> log.debug("✅ Created relation {} -> {} -> {}", in, relation, out))
                .doOnError(e -> log.error("❌ Failed to create relation: {}", e.getMessage()));
    }

    @Override
    public <T> Mono<T> insertRelation(String table, Object data, Class<T> resultType) {
        return rpc("insert_relation", table, data)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .map(response -> mapper.convertValue(response.result(), resultType))
                .doOnSuccess(r -> log.debug("✅ Inserted relation into {}", table))
                .doOnError(e -> log.error("❌ Failed to insert relation: {}", e.getMessage()));
    }

    // ============================================================
    // LIVE QUERIES
    // ============================================================

    @Override
    public Mono<String> live(String table, boolean diff) {
        return rpc("live", table, diff)
                .map(response -> mapper.convertValue(response.result(), String.class))
                .doOnSuccess(id -> log.debug("✅ Started live query on {}, id: {}", table, id))
                .doOnError(e -> log.error("❌ Failed to start live query: {}", e.getMessage()));
    }

    @Override
    public Mono<Void> kill(String liveQueryId) {
        return rpc("kill", liveQueryId)
                .then()
                .doOnSuccess(v -> {
                    liveQuerySinks.remove(liveQueryId);
                    log.debug("✅ Killed live query {}", liveQueryId);
                })
                .doOnError(e -> log.error("❌ Failed to kill live query: {}", e.getMessage()));
    }

    @Override
    public Flux<Map<String, Object>> listenToLiveQuery(String liveQueryId) {
        Sinks.Many<Map<String, Object>> sink = Sinks.many().multicast().onBackpressureBuffer();
        liveQuerySinks.put(liveQueryId, sink);

        return sink.asFlux()
            .doFinally(signal -> liveQuerySinks.remove(liveQueryId))
            .doOnSubscribe(s -> log.debug("👂 Listening to live query {}", liveQueryId));
    }

    @Override
    public Mono<Void> disconnect() {
        pendingRequests.clear();
        liveQuerySinks.clear();
        isConnected = false;
        sendSink.tryEmitComplete();
        log.info("🔌 Disconnected from SurrealDB");
        return Mono.empty();
    }

    // ============================================================
    // TRANSACTIONS
    // ============================================================

    @Override
    public <T> Mono<T> transaction(Function<OneirosTransaction, Mono<T>> transactionBlock) {
        return transactionManager.execute(transactionBlock)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    @Override
    public <T> Flux<T> transactionMany(Function<OneirosTransaction, Flux<T>> transactionBlock) {
        return transactionManager.executeMany(transactionBlock)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }


    // --- Private Helpers ---

    private Mono<RpcResponse> rpc(String method, Object... params) {
        return waitForConnection().then(Mono.defer(() -> sendRpc(method, params)));
    }

    private Mono<Void> waitForConnection() {
        // If already connected, return immediately
        if (isConnected) {
            return Mono.empty();
        }

        // If connecting, wait for init to complete
        if (isConnecting) {
            return initCompleteSink.asMono()
                .timeout(java.time.Duration.ofSeconds(20))
                .doOnError(err -> log.error("⏱️ Timeout waiting for connection: {}", err.getMessage()));
        }

        // Not connected -> start connection
        return connect()
            .timeout(java.time.Duration.ofSeconds(20))
            .doOnError(err -> log.error("⏱️ Timeout during connection: {}", err.getMessage()));
    }

    private Mono<RpcResponse> sendRpc(String method, Object... params) {
        String id = UUID.randomUUID().toString();
        RpcRequest request = new RpcRequest(id, method, List.of(params));

        Sinks.One<RpcResponse> responseSink = Sinks.one();
        pendingRequests.put(id, responseSink);

        try {
            String json = mapper.writeValueAsString(request);
            log.debug("📤 RPC [{}]: {}", method, json);

            // Send the message through the send sink (will be picked up by sendHandler)
            Sinks.EmitResult result = sendSink.tryEmitNext(json);
            if (result.isFailure()) {
                pendingRequests.remove(id);
                return Mono.error(new RuntimeException("Failed to send RPC message: " + result));
            }

            // Wait for the response
            return responseSink.asMono()
                .timeout(java.time.Duration.ofSeconds(30))
                .doOnError(err -> pendingRequests.remove(id));
        } catch (Exception e) {
            pendingRequests.remove(id);
            return Mono.error(e);
        }
    }
}