package io.oneiros.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.security.Security;

@Setter
@Getter
@ConfigurationProperties(prefix = "oneiros")
public class OneirosProperties {

    // Getter und Setter (Lombok @Data wäre hier praktisch, wenn du es nutzt, sonst generieren)
    /**
     * Die WebSocket URL zur SurrealDB Instanz.
     * Beispiel: ws://localhost:8000/rpc
     */
    private String url = "ws://localhost:8000/rpc";

    /**
     * Der Namespace in SurrealDB.
     */
    private String namespace;

    /**
     * Der Datenbank-Name in SurrealDB.
     */
    private String database;

    /**
     * Der Benutzername für die Authentifizierung.
     */
    private String username;

    /**
     * Das Passwort für die Authentifizierung.
     */
    private String password;

    private Security security = new Security();

    @Setter
    @Getter
    public static class Security {
        private boolean enabled = false; // Standardmäßig aus
        private String key = ""; // Der geheime Schlüssel (Muss > 16 Zeichen sein)

    }

    private Cache cache = new Cache(); // <--- NEU

    @Setter
    @Getter
    public static class Cache {
        private boolean enabled = true;
        private long ttlSeconds = 60; // Daten bleiben 60s frisch
        private long maxSize = 10000; // Maximal 10.000 Objekte im RAM

    }
}