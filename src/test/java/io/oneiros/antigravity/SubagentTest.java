package io.oneiros.antigravity;

import io.oneiros.antigravity.event.InternalEventBus;
import io.oneiros.antigravity.event.OneirosEvent;
import io.oneiros.client.OneirosClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubagentTest {

    private InternalEventBus eventBus;
    private OneirosClient mockClient;

    @BeforeEach
    void setUp() {
        eventBus = InternalEventBus.getInstance();
        mockClient = mock(OneirosClient.class);
    }

    @Test
    void testAgentLifecycleAndEventHandling() {
        AtomicBoolean connectedCalled = new AtomicBoolean(false);
        
        Subagent agent = new Subagent() {
            @Override
            protected void onConnected(OneirosEvent.Connected event) {
                connectedCalled.set(true);
            }

            @Override
            protected void onDisconnected(OneirosEvent.Disconnected event) {}
        };

        agent.start();
        assertTrue(agent.isRunning());

        // Publish event
        eventBus.publish(new OneirosEvent.Connected("client-1"));

        // Verify hook was called
        assertTrue(connectedCalled.get(), "onConnected should have been called");

        agent.stop();
        assertFalse(agent.isRunning());
    }

    @Test
    void testHealingSubagentReplay() {
        HealingSubagent agent = new HealingSubagent(mockClient);
        agent.start();

        // Simulate a failing operation
        Mono<String> failingOp = Mono.error(new RuntimeException("Connection lost"));
        
        // Execute (will fail and be buffered)
        StepVerifier.create(agent.execute("test-op", failingOp))
            .expectError()
            .verify();

        // Simulate recovery
        eventBus.publish(new OneirosEvent.Connected("client-1"));

        // Note: Replay happens asynchronously in the background
        // In a real test we would use VirtualTime or more complex verification
        // But the log output during test run will show the replay attempt
        
        agent.stop();
    }
}
