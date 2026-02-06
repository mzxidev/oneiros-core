package io.oneiros.config;

import io.oneiros.client.OneirosClient;
import io.oneiros.pool.OneirosConnectionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs Oneiros connection status on application startup.
 *
 * @author Oneiros Team
 * @since 0.2.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OneirosStartupLogger {

    private final OneirosClient client;
    private final OneirosProperties properties;

    // ANSI Colors
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    @EventListener(ApplicationReadyEvent.class)
    public void logConnectionStatus() {
        System.out.println("\n" + CYAN + BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println(CYAN + BOLD + "              🌊 ONEIROS DATABASE CLIENT 🌊" + RESET);
        System.out.println(CYAN + BOLD + "═══════════════════════════════════════════════════════════" + RESET);

        System.out.println(CYAN + "   URL:        " + RESET + properties.getUrl());
        System.out.println(CYAN + "   Namespace:  " + RESET + properties.getNamespace());
        System.out.println(CYAN + "   Database:   " + RESET + properties.getDatabase());
        System.out.println(CYAN + "   Username:   " + RESET + properties.getUsername());

        boolean isConnected = client.isConnected();
        String statusColor = isConnected ? GREEN : RED;
        String statusSymbol = isConnected ? "✅" : "❌";
        String statusText = isConnected ? "CONNECTED" : "DISCONNECTED";

        System.out.println(CYAN + "   Status:     " + statusColor + BOLD + statusSymbol + " " + statusText + RESET);

        // Show pool stats if using connection pool
        if (client instanceof OneirosConnectionPool pool) {
            OneirosConnectionPool.PoolStats stats = pool.getStats();
            System.out.println(CYAN + "   Pool:       " + RESET + stats.healthy() + "/" + stats.total() + " connections healthy");
            System.out.println(CYAN + "               " + RESET + String.format("%.1f%% health rate", stats.healthPercentage()));
        }

        System.out.println(CYAN + BOLD + "═══════════════════════════════════════════════════════════" + RESET + "\n");

        if (!isConnected) {
            log.warn("⚠️ Oneiros is NOT connected to SurrealDB!");
            log.warn("   Please check your configuration:");
            log.warn("   - Is SurrealDB running at {}?", properties.getUrl());
            log.warn("   - Are credentials correct (user: {})?", properties.getUsername());
            log.warn("   - Does namespace '{}' and database '{}' exist?",
                properties.getNamespace(), properties.getDatabase());
        }
    }
}
