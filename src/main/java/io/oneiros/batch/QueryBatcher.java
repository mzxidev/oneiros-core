package io.oneiros.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oneiros.client.OneirosClient;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reactive Query Batcher for Performance 2.0.
 * Groups multiple small SurrealQL queries into a single transaction batch.
 */
public class QueryBatcher {
    private static final Logger log = LoggerFactory.getLogger(QueryBatcher.class);

    private final OneirosClient client;
    private final ObjectMapper mapper;
    private final Sinks.Many<QueuedQuery<?>> querySink;

    public QueryBatcher(OneirosClient client, ObjectMapper mapper, int maxBatchSize, Duration maxWaitTime) {
        this.client = client;
        this.mapper = mapper;
        // SECURITY FIX: Limit buffer capacity to 10.000 entries to prevent OOM
        this.querySink = Sinks.many().multicast().onBackpressureBuffer(10000);

        // Start the batch processor
        this.querySink.asFlux()
                .bufferTimeout(maxBatchSize, maxWaitTime)
                .filter(batch -> !batch.isEmpty())
                .subscribe(this::processBatch);
    }

    /**
     * Executes a query via the batcher.
     */
    public <T> Flux<T> query(String sql, Map<String, Object> params, Class<T> resultType) {
        Sinks.Many<T> resultSink = Sinks.many().unicast().onBackpressureBuffer();
        QueuedQuery<T> queuedQuery = new QueuedQuery<>(sql, params, resultType, resultSink);
        
        Sinks.EmitResult result = querySink.tryEmitNext(queuedQuery);
        if (result.isFailure()) {
            return Flux.error(new RuntimeException("Failed to queue query in batcher: " + result));
        }

        return resultSink.asFlux();
    }

    private void processBatch(List<QueuedQuery<?>> batch) {
        log.debug("📦 Processing batch of {} queries", batch.size());

        if (batch.size() == 1) {
            // No need to wrap single query in transaction
            QueuedQuery<?> q = batch.getFirst();
            client.query(q.sql, q.params, Object.class)
                    .subscribe(
                            val -> ((Sinks.Many<Object>)q.resultSink).tryEmitNext(val),
                            err -> q.resultSink.tryEmitError(err),
                            () -> q.resultSink.tryEmitComplete()
                    );
            return;
        }

        // Combine into BEGIN TRANSACTION; ... COMMIT TRANSACTION;
        StringBuilder batchedSql = new StringBuilder("BEGIN TRANSACTION;\n");
        for (QueuedQuery<?> q : batch) {
            batchedSql.append(q.sql);
            if (!q.sql.trim().endsWith(";")) {
                batchedSql.append(";");
            }
            batchedSql.append("\n");
        }
        batchedSql.append("COMMIT TRANSACTION;");

        // Execute as one large query
        // Note: SurrealDB returns a list of results, one for each statement
        client.query(batchedSql.toString(), Map.of(), Map.class)
                .collectList()
                .subscribe(
                        results -> demultiplexResults(batch, results),
                        err -> {
                            log.error("💥 Batch execution failed", err);
                            batch.forEach(q -> q.resultSink.tryEmitError(err));
                        }
                );
    }

    private void demultiplexResults(List<QueuedQuery<?>> batch, List<Map> results) {
        // results contains: [ {status: OK, result: null}, ... statements ..., {status: OK, result: null} ]
        // The first and last are for BEGIN and COMMIT.
        
        // Skip first (BEGIN)
        int resultIndex = 1;
        
        for (QueuedQuery<?> q : batch) {
            if (resultIndex >= results.size() - 1) {
                q.resultSink.tryEmitError(new RuntimeException("Missing result in batch for query: " + q.sql));
                continue;
            }

            Map<String, Object> statementResult = results.get(resultIndex++);
            String status = (String) statementResult.get("status");
            Object data = statementResult.get("result");

            if ("OK".equals(status)) {
                if (data instanceof List<?> list) {
                    for (Object item : list) {
                        Object typed = mapper.convertValue(item, q.resultType);
                        ((Sinks.Many<Object>)q.resultSink).tryEmitNext(typed);
                    }
                } else if (data != null) {
                    Object typed = mapper.convertValue(data, q.resultType);
                    ((Sinks.Many<Object>)q.resultSink).tryEmitNext(typed);
                }
                q.resultSink.tryEmitComplete();
            } else {
                q.resultSink.tryEmitError(new RuntimeException("Query in batch failed: " + statementResult.get("detail")));
            }
        }
    }

    @Value
    private static class QueuedQuery<T> {
        String sql;
        Map<String, Object> params;
        Class<T> resultType;
        Sinks.Many<T> resultSink;
    }
}
