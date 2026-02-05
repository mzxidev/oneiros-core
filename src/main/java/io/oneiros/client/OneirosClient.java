package io.oneiros.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface OneirosClient {

    /**
     * Baut die Verbindung auf und führt Signin + Use Namespace/Db aus.
     */
    Mono<Void> connect();

    /**
     * Sendet eine SQL-Query an die DB.
     * @param sql Der SurrealQL String (z.B. "SELECT * FROM user")
     * @param resultType Die Klasse, in die das Ergebnis gemappt werden soll
     */
    <T> Flux<T> query(String sql, Class<T> resultType);

    /**
     * Sendet eine SQL-Query mit Parametern an die DB.
     * @param sql Der SurrealQL String (z.B. "SELECT * FROM user WHERE id = $id")
     * @param params Die Parameter-Map
     * @param resultType Die Klasse, in die das Ergebnis gemappt werden soll
     */
    <T> Flux<T> query(String sql, Map<String, Object> params, Class<T> resultType);

    /**
     * Listens to real-time notifications from a LIVE SELECT query.
     * @param liveQueryId The UUID of the live query
     * @return Flux of notification events
     */
    Flux<Map<String, Object>> listenToLiveQuery(String liveQueryId);

    /**
     * Schließt die Verbindung.
     */
    Mono<Void> disconnect();
}

