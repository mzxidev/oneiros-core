package io.oneiros.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveOneirosRepository<T, ID> {

    /**
     * Speichert oder aktualisiert eine Entität.
     */
    Mono<T> save(T entity);

    /**
     * Findet eine Entität anhand ihrer ID.
     */
    Mono<T> findById(ID id);

    /**
     * Gibt alle Entitäten der Tabelle zurück.
     */
    Flux<T> findAll();

    /**
     * Löscht eine Entität anhand der ID.
     */
    Mono<Void> deleteById(ID id);

    /**
     * Aktiviert Live-Updates für diese Tabelle (SurrealDB Live Queries).
     */
    Flux<T> subscribe();
}