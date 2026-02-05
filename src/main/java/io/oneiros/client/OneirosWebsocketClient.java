package io.oneiros.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.oneiros.client.rpc.RpcRequest;
import io.oneiros.client.rpc.RpcResponse;
import io.oneiros.config.OneirosProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class OneirosWebsocketClient implements OneirosClient {

    private final OneirosProperties properties;
    private final ObjectMapper mapper;
    private final ReactorNettyWebSocketClient client;
    private final CircuitBreaker circuitBreaker;

    // Die aktive Session wird über einen Sink bereitgestellt
    private final Sinks.Many<WebSocketSession> sessionSink = Sinks.many().replay().latest();
    private volatile boolean isConnecting = false;
    private volatile boolean isConnected = false;

    // Speicher für offene Anfragen: Request-ID -> Antwort-Kanal
    private final Map<String, Sinks.One<RpcResponse>> pendingRequests = new ConcurrentHashMap<>();

    // Live Query support: Live Query ID -> Event Sink
    private final Map<String, Sinks.Many<Map<String, Object>>> liveQuerySinks = new ConcurrentHashMap<>();

    public OneirosWebsocketClient(OneirosProperties properties, ObjectMapper mapper, CircuitBreaker circuitBreaker) {
        this.properties = properties;
        this.mapper = mapper;
        this.circuitBreaker = circuitBreaker;
        this.client = new ReactorNettyWebSocketClient();
    }

    @Override
    public Mono<Void> connect() {
        if (isConnected) {
            return Mono.empty();
        }

        if (isConnecting) {
            // Warte darauf, dass die laufende Verbindung fertig wird
            return sessionSink.asFlux().next().then();
        }

        isConnecting = true;
        URI uri = URI.create(properties.getUrl());

        // Erstelle HTTP Headers für SurrealDB 2.0+
        HttpHeaders headers = new HttpHeaders();
        headers.set("Sec-WebSocket-Protocol", "json");  // Explizites JSON-Format

        return client.execute(uri, headers, session -> {
                    log.info("🌐 Oneiros connected to SurrealDB at {}", uri);

                    // Empfange Nachrichten im Hintergrund
                    Mono<Void> input = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(this::handleIncomingMessage)
                            .then();

                    // Authentifizierung und Namespace-Wahl
                    Mono<Void> init = rpcWithSession(session, "signin", Map.of("user", properties.getUsername(), "pass", properties.getPassword()))
                            .then(rpcWithSession(session, "use", properties.getNamespace(), properties.getDatabase()))
                            .then()
                            .doOnSuccess(v -> {
                                // Erst NACH erfolgreicher Auth die Session publishen
                                sessionSink.tryEmitNext(session);
                                isConnected = true;
                                isConnecting = false;
                            })
                            .doOnError(err -> {
                                log.error("💥 Init failed: {}", err.getMessage());
                                isConnecting = false;
                                sessionSink.tryEmitError(err);
                            });

                    // Beide Streams parallel laufen lassen
                    return init.thenMany(input).then();
                })
                .retry(3)
                .doOnError(e -> {
                    log.error("💥 Connection fatal error after retries", e);
                    isConnecting = false;
                    isConnected = false;
                    sessionSink.tryEmitError(e);
                });
    }

    private void handleIncomingMessage(String json) {
        log.trace("📥 RX: {}", json);

        try {
            RpcResponse response = mapper.readValue(json, RpcResponse.class);

            if (response.id() != null) {
                Sinks.One<RpcResponse> sink = pendingRequests.remove(response.id());
                if (sink != null) {
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
            log.error("💥 Failed to parse incoming message: {}", e.getMessage());
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
                        List<Map<String, Object>> queryResults = mapper.convertValue(
                                response.result(), new TypeReference<List<Map<String, Object>>>() {}
                        );
                        if (queryResults == null || queryResults.isEmpty()) return Flux.empty();

                        Map<String, Object> firstSet = queryResults.getFirst();
                        if (!"OK".equals(firstSet.get("status"))) {
                            return Flux.error(new RuntimeException("SurrealDB Error: " + firstSet.get("detail")));
                        }

                        Object rawResultData = firstSet.get("result");
                        if (rawResultData == null) return Flux.empty();

                        CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, resultType);
                        List<T> typedList = mapper.convertValue(rawResultData, listType);
                        return Flux.fromIterable(typedList);
                    } catch (Exception e) {
                        return Flux.error(e);
                    }
                });
    }

    @Override
    public Flux<Map<String, Object>> listenToLiveQuery(String liveQueryId) {
        Sinks.Many<Map<String, Object>> sink = Sinks.many().multicast().onBackpressureBuffer();
        liveQuerySinks.put(liveQueryId, sink);

        return sink.asFlux()
            .doFinally(signal -> liveQuerySinks.remove(liveQueryId));
    }

    @Override
    public Mono<Void> disconnect() {
        // Netty managed Close meist selbst, aber wir können hier clearen
        pendingRequests.clear();
        return Mono.empty();
    }


    // --- Private Helpers ---

    private Mono<RpcResponse> rpc(String method, Object... params) {
        return getOrWaitForSession().flatMap(activeSession -> rpcWithSession(activeSession, method, params));
    }

    private Mono<WebSocketSession> getOrWaitForSession() {
        if (isConnected) {
            // Bereits verbunden, hole die letzte Session aus dem Replay-Sink
            return sessionSink.asFlux().next();
        }

        // Nicht verbunden -> starte Verbindung und warte auf Session
        return connect().then(sessionSink.asFlux().next());
    }

    private Mono<RpcResponse> rpcWithSession(WebSocketSession session, String method, Object... params) {
        String id = UUID.randomUUID().toString();
        RpcRequest request = new RpcRequest(id, method, List.of(params));

        Sinks.One<RpcResponse> sink = Sinks.one();
        pendingRequests.put(id, sink);

        try {
            String json = mapper.writeValueAsString(request);
            log.debug("📤 RPC [{}]: {}", method, json);

            return session.send(Mono.just(session.textMessage(json)))
                    .then(sink.asMono())
                    .doOnError(err -> pendingRequests.remove(id));
        } catch (Exception e) {
            pendingRequests.remove(id);
            return Mono.error(e);
        }
    }
}