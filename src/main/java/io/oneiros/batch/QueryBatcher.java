package io.oneiros.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oneiros.client.OneirosClient;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
            executeSingle(batch.getFirst());
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
        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
        Map<String, Object> noParams = Map.of();
        client.query(batchedSql.toString(), noParams, mapClass)
                .collectList()
                .subscribe(
                        results -> demultiplexResults(batch, results),
                        err -> {
                            log.error("💥 Batch execution failed", err);
                            batch.forEach(q -> q.resultSink.tryEmitError(err));
                        });
    }

    // Called via lambda on the subscribe() above — IDE cannot trace the inferred
    // type chain.
    @SuppressWarnings("unused")
    private void demultiplexResults(List<QueuedQuery<?>> batch, List<Map<String, Object>> results) {
        // results contains: [ {status: OK, result: null}, ... statements ..., {status:
        // OK, result: null} ]
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
                emitResults(q, data);
                q.resultSink.tryEmitComplete();
            } else {
                q.resultSink
                        .tryEmitError(new RuntimeException("Query in batch failed: " + statementResult.get("detail")));
            }
        }
    }

    /**
     * Wildcard capture helper: gives the '?' a name so the compiler can verify
     * that resultSink and the emitted value share the same type T — no cast needed.
     */
    private <T> void executeSingle(QueuedQuery<T> q) {
        client.query(q.sql, q.params, q.resultType)
                .subscribe(
                        q.resultSink::tryEmitNext,
                        q.resultSink::tryEmitError,
                        q.resultSink::tryEmitComplete);
    }

    /**
     * Wildcard capture helper for demultiplexing: converts raw data into type T
     * and emits it into the correctly-typed sink without an unchecked cast.
     */
    private <T> void emitResults(QueuedQuery<T> q, Object data) {
        if (data instanceof List<?> list) {
            for (Object item : list) {
                T typed = mapper.convertValue(item, q.resultType);
                q.resultSink.tryEmitNext(typed);
            }
        } else if (data != null) {
            T typed = mapper.convertValue(data, q.resultType);
            q.resultSink.tryEmitNext(typed);
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
