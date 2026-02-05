package io.oneiros.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
     * Schließt die Verbindung.
     */
    Mono<Void> disconnect();
}