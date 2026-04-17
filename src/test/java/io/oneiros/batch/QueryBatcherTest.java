package io.oneiros.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oneiros.client.OneirosClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueryBatcherTest {

    private OneirosClient mockClient;
    private ObjectMapper mapper;
    private QueryBatcher batcher;

    @BeforeEach
    void setUp() {
        mockClient = mock(OneirosClient.class);
        mapper = new ObjectMapper();
        // Batch size 5, timeout 100ms
        batcher = new QueryBatcher(mockClient, mapper, 5, Duration.ofMillis(100));
    }

    @Test
    void testBatchingMultipleQueries() {
        // Prepare mock response for the batched transaction
        // BEGIN, Q1, Q2, COMMIT
        List<Map<String, Object>> mockResults = List.of(
                createResultMap("OK", null), // BEGIN
                createResultMap("OK", List.of(Map.of("id", "user:1", "name", "Alice"))), // Q1
                createResultMap("OK", List.of(Map.of("id", "user:2", "name", "Bob"))),   // Q2
                createResultMap("OK", null)  // COMMIT
        );

        when(mockClient.query(anyString(), anyMap(), eq(Map.class)))
                .thenReturn(Flux.fromIterable(mockResults));

        // Execute two queries
        Flux<Map> res1 = batcher.query("SELECT * FROM user:1", Map.of(), Map.class);
        Flux<Map> res2 = batcher.query("SELECT * FROM user:2", Map.of(), Map.class);

        // Verify results
        StepVerifier.create(res1)
                .expectNextMatches(m -> "Alice".equals(m.get("name")))
                .verifyComplete();

        StepVerifier.create(res2)
                .expectNextMatches(m -> "Bob".equals(m.get("name")))
                .verifyComplete();

        // Verify that client.query was called only once with the batched SQL
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClient, times(1)).query(sqlCaptor.capture(), anyMap(), eq(Map.class));
        
        String capturedSql = sqlCaptor.getValue();
        assertTrue(capturedSql.contains("BEGIN TRANSACTION"));
        assertTrue(capturedSql.contains("SELECT * FROM user:1"));
        assertTrue(capturedSql.contains("SELECT * FROM user:2"));
        assertTrue(capturedSql.contains("COMMIT TRANSACTION"));
    }

    private Map<String, Object> createResultMap(String status, Object result) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("status", status);
        map.put("result", result);
        return map;
    }
}
