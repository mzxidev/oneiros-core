package io.oneiros.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central audit logging service for security events.
 *
 * <p>Provides:
 * <ul>
 *   <li>Structured audit logging</li>
 *   <li>Multiple output handlers (file, SIEM, database)</li>
 *   <li>Event filtering by severity</li>
 *   <li>Compliance reporting</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * SecurityAuditLogger audit = SecurityAuditLogger.getInstance();
 *
 * // Log encryption event
 * audit.log(SecurityAuditEvent.builder()
 *     .type(EventType.ENCRYPTION)
 *     .action("encrypt_field")
 *     .resource("users.credit_card")
 *     .outcome(Outcome.SUCCESS)
 *     .build());
 *
 * // Log failed authentication
 * audit.log(SecurityAuditEvent.builder()
 *     .type(EventType.AUTHENTICATION)
 *     .action("signin")
 *     .principal("user@example.com")
 *     .outcome(Outcome.FAILURE)
 *     .severity(Severity.WARN)
 *     .errorMessage("Invalid credentials")
 *     .build());
 * }</pre>
 *
 * @since 0.4.3
 */
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);

    /**
     * SECURITY FIX: Bill Pugh Singleton (Initialization-on-demand holder idiom).
     * Thread-safe, lazy-loaded, no synchronization overhead.
     * Preferred over double-checked locking.
     */
    private static class Holder {
        static final SecurityAuditLogger INSTANCE = new SecurityAuditLogger();
    }

    private final List<Consumer<SecurityAuditEvent>> handlers = new CopyOnWriteArrayList<>();
    private final Map<String, Long> eventCounters = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;
    private volatile SecurityAuditEvent.Severity minSeverity = SecurityAuditEvent.Severity.INFO;

    private SecurityAuditLogger() {
        // Register default SLF4J handler
        registerHandler(this::logToSlf4j);
        log.info("🔍 SecurityAuditLogger initialized");
    }

    /**
     * Gets the singleton instance (thread-safe, lazy-loaded).
     */
    public static SecurityAuditLogger getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Logs a security audit event.
     *
     * @param event the event to log
     */
    public void log(SecurityAuditEvent event) {
        if (!enabled) {
            return;
        }

        if (event.severity().ordinal() < minSeverity.ordinal()) {
            return; // Below minimum severity
        }

        // Update counters
        String key = event.type() + ":" + event.outcome();
        eventCounters.merge(key, 1L, Long::sum);

        // Dispatch to all handlers
        for (Consumer<SecurityAuditEvent> handler : handlers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                log.error("Audit handler failed: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Registers a custom event handler.
     *
     * <p>Example handlers:
     * <ul>
     *   <li>Write to audit log file</li>
     *   <li>Send to SIEM system</li>
     *   <li>Store in database</li>
     *   <li>Send alerts for critical events</li>
     * </ul>
     *
     * @param handler the event handler
     */
    public void registerHandler(Consumer<SecurityAuditEvent> handler) {
        handlers.add(handler);
        log.debug("Registered audit handler: {}", handler.getClass().getSimpleName());
    }

    /**
     * Removes a registered handler.
     */
    public void unregisterHandler(Consumer<SecurityAuditEvent> handler) {
        handlers.remove(handler);
    }

    /**
     * Sets the minimum severity level to log.
     *
     * @param severity minimum severity (default: INFO)
     */
    public void setMinSeverity(SecurityAuditEvent.Severity severity) {
        this.minSeverity = severity;
        log.info("Audit min severity set to: {}", severity);
    }

    /**
     * Enables or disables audit logging.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("Audit logging {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Returns event statistics.
     *
     * @return map of event type:outcome to count
     */
    public Map<String, Long> getEventCounters() {
        return Map.copyOf(eventCounters);
    }

    /**
     * Resets event counters.
     */
    public void resetCounters() {
        eventCounters.clear();
        log.debug("Audit counters reset");
    }

    /**
     * Default SLF4J handler.
     */
    private void logToSlf4j(SecurityAuditEvent event) {
        String message = event.toLogMessage();

        switch (event.severity()) {
            case DEBUG -> log.debug(message);
            case INFO -> log.info(message);
            case WARN -> log.warn(message);
            case ERROR -> log.error(message);
            case CRITICAL -> log.error("🚨 CRITICAL: {}", message);
        }
    }

    // ========== Convenience Methods ==========

    /**
     * Logs an encryption event.
     */
    public void logEncryption(String resource, boolean success) {
        log(SecurityAuditEvent.builder()
                .type(SecurityAuditEvent.EventType.ENCRYPTION)
                .action("encrypt")
                .resource(resource)
                .outcome(success ? SecurityAuditEvent.Outcome.SUCCESS : SecurityAuditEvent.Outcome.FAILURE)
                .build());
    }

    /**
     * Logs a decryption event.
     */
    public void logDecryption(String resource, boolean success) {
        log(SecurityAuditEvent.builder()
                .type(SecurityAuditEvent.EventType.DECRYPTION)
                .action("decrypt")
                .resource(resource)
                .outcome(success ? SecurityAuditEvent.Outcome.SUCCESS : SecurityAuditEvent.Outcome.FAILURE)
                .build());
    }

    /**
     * Logs a key rotation event.
     */
    public void logKeyRotation(String keyId, int oldVersion, int newVersion) {
        log(SecurityAuditEvent.builder()
                .type(SecurityAuditEvent.EventType.KEY_ROTATION)
                .action("rotate_key")
                .resource(keyId)
                .outcome(SecurityAuditEvent.Outcome.SUCCESS)
                .severity(SecurityAuditEvent.Severity.WARN)
                .metadata(Map.of(
                        "oldVersion", String.valueOf(oldVersion),
                        "newVersion", String.valueOf(newVersion)
                ))
                .build());
    }

    /**
     * Logs an authentication event.
     */
    public void logAuthentication(String principal, boolean success, String errorMessage) {
        log(SecurityAuditEvent.builder()
                .type(SecurityAuditEvent.EventType.AUTHENTICATION)
                .action("authenticate")
                .principal(principal)
                .outcome(success ? SecurityAuditEvent.Outcome.SUCCESS : SecurityAuditEvent.Outcome.FAILURE)
                .severity(success ? SecurityAuditEvent.Severity.INFO : SecurityAuditEvent.Severity.WARN)
                .errorMessage(errorMessage)
                .build());
    }

    /**
     * Logs a rate limit event.
     */
    public void logRateLimit(String resource, int requested, int available) {
        log(SecurityAuditEvent.builder()
                .type(SecurityAuditEvent.EventType.RATE_LIMIT)
                .action("throttle")
                .resource(resource)
                .outcome(SecurityAuditEvent.Outcome.THROTTLED)
                .severity(SecurityAuditEvent.Severity.WARN)
                .metadata(Map.of(
                        "requested", String.valueOf(requested),
                        "available", String.valueOf(available)
                ))
                .build());
    }

    /**
     * Logs a configuration change event.
     */
    public void logConfigChange(String parameter, String oldValue, String newValue) {
        log(SecurityAuditEvent.builder()
                .type(SecurityAuditEvent.EventType.CONFIG_CHANGE)
                .action("update_config")
                .resource(parameter)
                .outcome(SecurityAuditEvent.Outcome.SUCCESS)
                .severity(SecurityAuditEvent.Severity.WARN)
                .metadata(Map.of(
                        "oldValue", oldValue,
                        "newValue", newValue
                ))
                .build());
    }
}
