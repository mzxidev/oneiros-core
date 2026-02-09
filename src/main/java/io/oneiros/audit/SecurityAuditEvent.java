package io.oneiros.audit;

import java.time.Instant;
import java.util.Map;

/**
 * Security audit event for tracking sensitive operations.
 *
 * <p>Records security-relevant events such as:
 * <ul>
 *   <li>Encryption/Decryption operations</li>
 *   <li>Key rotation events</li>
 *   <li>Authentication attempts</li>
 *   <li>Access control decisions</li>
 *   <li>Configuration changes</li>
 * </ul>
 *
 * @since 0.4.3
 */
public record SecurityAuditEvent(
        /**
         * Unique event ID for correlation.
         */
        String eventId,

        /**
         * Event type (e.g., "ENCRYPTION", "KEY_ROTATION", "AUTH_FAILED").
         */
        EventType type,

        /**
         * Event severity level.
         */
        Severity severity,

        /**
         * When the event occurred.
         */
        Instant timestamp,

        /**
         * User/Service that triggered the event.
         */
        String principal,

        /**
         * Resource being accessed/modified.
         */
        String resource,

        /**
         * Action performed.
         */
        String action,

        /**
         * Outcome of the action.
         */
        Outcome outcome,

        /**
         * Additional contextual information.
         */
        Map<String, String> metadata,

        /**
         * Error message if outcome is FAILURE.
         */
        String errorMessage
) {

    public enum EventType {
        ENCRYPTION,
        DECRYPTION,
        KEY_ROTATION,
        KEY_ACCESS,
        AUTHENTICATION,
        AUTHORIZATION,
        CONFIG_CHANGE,
        RATE_LIMIT,
        CIRCUIT_BREAKER,
        CONNECTION
    }

    public enum Severity {
        DEBUG,
        INFO,
        WARN,
        ERROR,
        CRITICAL
    }

    public enum Outcome {
        SUCCESS,
        FAILURE,
        DENIED,
        THROTTLED
    }

    /**
     * Builder for creating audit events.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventId = java.util.UUID.randomUUID().toString();
        private EventType type;
        private Severity severity = Severity.INFO;
        private Instant timestamp = Instant.now();
        private String principal = "system";
        private String resource;
        private String action;
        private Outcome outcome = Outcome.SUCCESS;
        private Map<String, String> metadata = Map.of();
        private String errorMessage;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder type(EventType type) {
            this.type = type;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder principal(String principal) {
            this.principal = principal;
            return this;
        }

        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder outcome(Outcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public SecurityAuditEvent build() {
            return new SecurityAuditEvent(
                    eventId, type, severity, timestamp, principal,
                    resource, action, outcome, metadata, errorMessage
            );
        }
    }

    /**
     * Formats the event as a structured log message.
     */
    public String toLogMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[AUDIT] ")
          .append(severity).append(" ")
          .append(type).append(" | ")
          .append("principal=").append(principal).append(" ")
          .append("action=").append(action).append(" ")
          .append("resource=").append(resource).append(" ")
          .append("outcome=").append(outcome);

        if (errorMessage != null && !errorMessage.isEmpty()) {
            sb.append(" error=\"").append(errorMessage).append("\"");
        }

        if (!metadata.isEmpty()) {
            sb.append(" metadata=").append(metadata);
        }

        return sb.toString();
    }
}
