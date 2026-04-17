package io.oneiros.health;

import io.oneiros.client.OneirosClient;
import io.oneiros.pool.OneirosConnectionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Oneiros database connections.
 * Provides health status for Spring Boot Actuator.
 *
 * @author Oneiros Team
 * @since 0.2.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
public class OneirosHealthIndicator implements HealthIndicator {

    private final OneirosClient client;

    @Override
    public Health health() {
        try {
            boolean isConnected = client.isConnected();

            if (isConnected) {
                Health.Builder builder = Health.up()
                    .withDetail("status", "Connected")
                    .withDetail("type", client.getClass().getSimpleName());

                // Add pool stats if using connection pool
                if (client instanceof OneirosConnectionPool pool) {
                    OneirosConnectionPool.PoolStats stats = pool.getStats();
                    builder.withDetail("pool", stats);
                }

                return builder.build();
            } else {
                return Health.down()
                    .withDetail("status", "Disconnected")
                    .withDetail("message", "No active connection to SurrealDB")
                    .build();
            }
        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
