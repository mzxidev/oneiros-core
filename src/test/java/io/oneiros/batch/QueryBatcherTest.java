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
                                createResultMap("OK", List.of(Map.of("id", "user:2", "name", "Bob"))), // Q2
                                createResultMap("OK", null) // COMMIT
                );

                @SuppressWarnings("unchecked")
                Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;

                when(mockClient.query(anyString(), anyMap(), eq(mapClass)))
                                .thenReturn(Flux.fromIterable(mockResults));

                // Execute two queries
                Flux<Map<String, Object>> res1 = batcher.query("SELECT * FROM user:1", Map.of(), mapClass);
                Flux<Map<String, Object>> res2 = batcher.query("SELECT * FROM user:2", Map.of(), mapClass);

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

        /**
         * Explicitly exercises the demultiplexResults path:
         * verifies that each query in a batch receives only its own result,
         * and that a failed statement emits an error to the correct sink only.
         */
        @Test
        void testDemultiplexResults_routesResultsToCorrectSinks() {
                // BEGIN, Q1 OK with data, Q2 ERR, COMMIT
                List<Map<String, Object>> mockResults = List.of(
                                createResultMap("OK", null), // BEGIN
                                createResultMap("OK", List.of(Map.of("id", "item:1", "value", 42))), // Q1 → OK
                                createResultMap("ERR", null, "unique constraint violated"), // Q2 → ERR
                                createResultMap("OK", null) // COMMIT
                );

                @SuppressWarnings("unchecked")
                Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;

                when(mockClient.query(anyString(), anyMap(), eq(mapClass)))
                                .thenReturn(Flux.fromIterable(mockResults));

                Flux<Map<String, Object>> res1 = batcher.query("SELECT * FROM item:1", Map.of(), mapClass);
                Flux<Map<String, Object>> res2 = batcher.query("INSERT INTO item SET value=99", Map.of(), mapClass);

                // Q1 should receive its data
                StepVerifier.create(res1)
                                .expectNextMatches(m -> Integer.valueOf(42).equals(m.get("value")))
                                .verifyComplete();

                // Q2 should receive an error, not data
                StepVerifier.create(res2)
                                .expectErrorMatches(e -> e.getMessage().contains("unique constraint violated"))
                                .verify();
        }

        private Map<String, Object> createResultMap(String status, Object result) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("status", status);
                map.put("result", result);
                return map;
        }

        private Map<String, Object> createResultMap(String status, Object result, String detail) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("status", status);
                map.put("result", result);
                map.put("detail", detail);
                return map;
        }
}
