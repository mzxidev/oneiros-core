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

    /**
     * Auto-connect on startup (default: true).
     * If true, connection to SurrealDB is established immediately when the application starts.
     * If false, connection is established lazily on the first request.
     */
    private boolean autoConnect = true;

    private Security security = new Security();

    @Setter
    @Getter
    public static class Security {
        private boolean enabled = false; // Standardmäßig aus
        private String key = ""; // Der geheime Schlüssel (Muss > 16 Zeichen sein)

    }

    private Cache cache = new Cache();

    private Migration migration = new Migration();

    private Pool pool = new Pool();

    @Setter
    @Getter
    public static class Cache {
        private boolean enabled = true;
        private long ttlSeconds = 60;
        private long maxSize = 10000;
    }

    @Setter
    @Getter
    public static class Migration {
        private boolean enabled = true;
        private String basePackage = "io.oneiros";
        private boolean dryRun = false;
    }

    @Setter
    @Getter
    public static class Pool {
        private boolean enabled = false;
        private int size = 5;
        private int minIdle = 2;
        private int maxWaitSeconds = 30;
        private long healthCheckInterval = 30;
        private boolean autoReconnect = true;
    }
}