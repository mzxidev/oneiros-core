package io.oneiros.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SecurityAuditLogger.
 */
class SecurityAuditLoggerTest {

    private SecurityAuditLogger logger;
    private List<SecurityAuditEvent> capturedEvents;

    @BeforeEach
    void setUp() {
        logger = SecurityAuditLogger.getInstance();
        logger.resetCounters();
        logger.setEnabled(true);
        logger.setMinSeverity(SecurityAuditEvent.Severity.DEBUG);

        capturedEvents = new ArrayList<>();
        logger.registerHandler(capturedEvents::add);
    }

    @Test
    @DisplayName("Should log security events")
    void shouldLogSecurityEvents() {
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .type(SecurityAuditEvent.EventType.ENCRYPTION)
                .action("encrypt")
                .resource("test_field")
                .outcome(SecurityAuditEvent.Outcome.SUCCESS)
                .build();

        logger.log(event);

        assertEquals(1, capturedEvents.size());
        assertEquals(event, capturedEvents.get(0));
    }

    @Test
    @DisplayName("Should filter by severity")
    void shouldFilterBySeverity() {
        logger.setMinSeverity(SecurityAuditEvent.Severity.WARN);

        // Should be filtered out
        logger.log(SecurityAuditEvent.builder()
                .type(SecurityAuditEvent.EventType.ENCRYPTION)
                .severity(SecurityAuditEvent.Severity.INFO)
                .build());

        // Should be logged
        logger.log(SecurityAuditEvent.builder()
                .type(SecurityAuditEvent.EventType.ENCRYPTION)
                .severity(SecurityAuditEvent.Severity.WARN)
                .build());

        logger.log(SecurityAuditEvent.builder()
                .type(SecurityAuditEvent.EventType.ENCRYPTION)
                .severity(SecurityAuditEvent.Severity.ERROR)
                .build());

        assertEquals(2, capturedEvents.size());
    }

    @Test
    @DisplayName("Should track event counters")
    void shouldTrackEventCounters() {
        logger.logEncryption("field1", true);
        logger.logEncryption("field2", true);
        logger.logEncryption("field3", false);
        logger.logDecryption("field4", true);

        Map<String, Long> counters = logger.getEventCounters();

        assertEquals(2L, counters.get("ENCRYPTION:SUCCESS"));
        assertEquals(1L, counters.get("ENCRYPTION:FAILURE"));
        assertEquals(1L, counters.get("DECRYPTION:SUCCESS"));
    }

    @Test
    @DisplayName("Should reset counters")
    void shouldResetCounters() {
        logger.logEncryption("field", true);
        logger.logDecryption("field", true);

        assertEquals(2, logger.getEventCounters().size());

        logger.resetCounters();

        assertEquals(0, logger.getEventCounters().size());
    }

    @Test
    @DisplayName("Should log convenience methods")
    void shouldLogConvenienceMethods() {
        logger.logEncryption("test_field", true);
        logger.logDecryption("test_field", false);
        logger.logKeyRotation("key-123", 1, 2);
        logger.logAuthentication("user@example.com", false, "Invalid password");
        logger.logRateLimit("api_endpoint", 10, 5);
        logger.logConfigChange("max_pool_size", "5", "10");

        assertEquals(6, capturedEvents.size());

        SecurityAuditEvent encEvent = capturedEvents.get(0);
        assertEquals(SecurityAuditEvent.EventType.ENCRYPTION, encEvent.type());
        assertEquals(SecurityAuditEvent.Outcome.SUCCESS, encEvent.outcome());

        SecurityAuditEvent decEvent = capturedEvents.get(1);
        assertEquals(SecurityAuditEvent.EventType.DECRYPTION, decEvent.type());
        assertEquals(SecurityAuditEvent.Outcome.FAILURE, decEvent.outcome());

        SecurityAuditEvent rotEvent = capturedEvents.get(2);
        assertEquals(SecurityAuditEvent.EventType.KEY_ROTATION, rotEvent.type());
        assertEquals("1", rotEvent.metadata().get("oldVersion"));
        assertEquals("2", rotEvent.metadata().get("newVersion"));
    }

    @Test
    @DisplayName("Should disable logging")
    void shouldDisableLogging() {
        logger.setEnabled(false);

        logger.logEncryption("field", true);

        assertEquals(0, capturedEvents.size());
    }

    @Test
    @DisplayName("Should format event as log message")
    void shouldFormatEventAsLogMessage() {
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .type(SecurityAuditEvent.EventType.AUTHENTICATION)
                .severity(SecurityAuditEvent.Severity.WARN)
                .principal("user@example.com")
                .action("signin")
                .resource("api")
                .outcome(SecurityAuditEvent.Outcome.FAILURE)
                .errorMessage("Invalid credentials")
                .metadata(Map.of("ip", "192.168.1.1"))
                .build();

        String message = event.toLogMessage();

        assertTrue(message.contains("[AUDIT]"));
        assertTrue(message.contains("WARN"));
        assertTrue(message.contains("AUTHENTICATION"));
        assertTrue(message.contains("principal=user@example.com"));
        assertTrue(message.contains("outcome=FAILURE"));
        assertTrue(message.contains("error=\"Invalid credentials\""));
        assertTrue(message.contains("metadata="));
    }

    @Test
    @DisplayName("Should handle multiple handlers")
    void shouldHandleMultipleHandlers() {
        List<SecurityAuditEvent> handler2Events = new ArrayList<>();
        logger.registerHandler(handler2Events::add);

        logger.logEncryption("field", true);

        assertEquals(1, capturedEvents.size());
        assertEquals(1, handler2Events.size());
        assertEquals(capturedEvents.get(0), handler2Events.get(0));
    }

    @Test
    @DisplayName("Should continue on handler failure")
    void shouldContinueOnHandlerFailure() {
        // Register a handler that throws
        logger.registerHandler(event -> {
            throw new RuntimeException("Handler error");
        });

        // Should not throw
        assertDoesNotThrow(() -> {
            logger.logEncryption("field", true);
        });

        // Other handlers should still work
        assertEquals(1, capturedEvents.size());
    }
}
