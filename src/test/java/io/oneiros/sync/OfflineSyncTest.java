package io.oneiros.sync;

import io.oneiros.antigravity.event.InternalEventBus;
import io.oneiros.antigravity.event.OneirosEvent;
import io.oneiros.client.OneirosClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfflineSyncTest {

    private OneirosClient mockRemote;
    private OneirosClient mockLocal;
    private OneirosHybridClient hybridClient;
    private InternalEventBus eventBus;

    @BeforeEach
    void setUp() {
        mockRemote = mock(OneirosClient.class);
        mockLocal = mock(OneirosClient.class);
        eventBus = InternalEventBus.getInstance();
        hybridClient = new OneirosHybridClient(mockRemote, mockLocal);
    }

    @Test
    void testOfflineBuffering() {
        // Given: Remote is disconnected
        when(mockRemote.isConnected()).thenReturn(false);
        when(mockLocal.create(anyString(), any(), any())).thenReturn(Mono.just(Map.of("id", "user:1")));

        // When: Create operation
        StepVerifier.create(hybridClient.create("user", Map.of("name", "Offline User"), Map.class))
                .expectNextCount(1)
                .verifyComplete();

        // Then: Local was used, remote was not
        verify(mockLocal).create(eq("user"), any(), eq(Map.class));
        verify(mockRemote, never()).create(anyString(), any(), any());

        // Now: Reconnect
        when(mockRemote.isConnected()).thenReturn(true);
        when(mockRemote.create(anyString(), any(), any())).thenReturn(Mono.just(Map.of("id", "user:1")));
        
        eventBus.publish(new OneirosEvent.Connected("remote-1"));

        // Verify sync happened (asynchronous, so we might need a small wait or use virtual time)
        // In this mock setup, we just verify the call was made
        verify(mockRemote, timeout(1000)).create(eq("user"), any(), eq(Object.class));
    }
}
