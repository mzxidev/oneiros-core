package io.oneiros.backup;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oneiros.client.OneirosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Framework-agnostic backup manager for Oneiros.
 *
 * Features:
 * - LZ4 block compression
 * - Streaming JSON processing (memory-efficient)
 * - Table-by-table backup/restore
 * - Metadata preservation
 */
public class OneirosBackupManager {
    private static final Logger log = LoggerFactory.getLogger(OneirosBackupManager.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final OneirosClient client;
    private final ObjectMapper objectMapper;
    private final String namespace;
    private final String database;

    public OneirosBackupManager(OneirosClient client, ObjectMapper objectMapper, String namespace, String database) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.namespace = namespace;
        this.database = database;
    }

    /**
     * Create a compressed backup.
     *
     * @param directory Directory to save backup
     * @return Path to created backup file
     */
    public Mono<File> createBackup(Path directory) {
        return Mono.fromCallable(() -> {
            log.info("💾 Starting backup: namespace={}, database={}", namespace, database);

            // Create backup file
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String filename = String.format("oneiros_backup_%s_%s_%s.onb", namespace, database, timestamp);
            File backupFile = directory.resolve(filename).toFile();

            Files.createDirectories(directory);

            try (FileOutputStream fos = new FileOutputStream(backupFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 DataOutputStream dos = new DataOutputStream(bos)) {

                // Write header
                BackupHeader header = BackupHeader.create();
                header.writeTo(dos);

                // Write compressed data
                try (Lz4BlockOutputStream lz4Out = new Lz4BlockOutputStream(dos);
                     BufferedOutputStream bufferedLz4 = new BufferedOutputStream(lz4Out)) {

                    writeBackupData(bufferedLz4);
                }
            }

            long sizeKB = backupFile.length() / 1024;
            log.info("✅ Backup created: {} ({} KB)", backupFile.getName(), sizeKB);

            return backupFile;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Restore from a compressed backup.
     *
     * @param backupFile Backup file to restore
     * @param dropExisting Whether to drop existing tables before restore
     */
    public Mono<Void> restoreBackup(File backupFile, boolean dropExisting) {
        return Mono.fromRunnable(() -> {
            log.info("📥 Starting restore from: {}", backupFile.getName());

            try (FileInputStream fis = new FileInputStream(backupFile);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 DataInputStream dis = new DataInputStream(bis)) {

                // Read header
                BackupHeader header = BackupHeader.readFrom(dis);
                log.info("📋 Backup metadata: version={}, timestamp={}",
                    header.version(), header.timestampAsInstant());

                // Read compressed data
                try (Lz4BlockInputStream lz4In = new Lz4BlockInputStream(dis);
                     BufferedInputStream bufferedLz4 = new BufferedInputStream(lz4In)) {

                    readBackupData(bufferedLz4, dropExisting);
                }

            } catch (IOException e) {
                throw new RuntimeException("Failed to restore backup", e);
            }

            log.info("✅ Restore completed");
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Write backup data using streaming JSON.
     */
    private void writeBackupData(OutputStream out) throws IOException {
        JsonFactory factory = new JsonFactory(objectMapper);

        try (JsonGenerator gen = factory.createGenerator(out)) {
            gen.writeStartObject();

            // Write metadata
            gen.writeObjectFieldStart("metadata");
            gen.writeStringField("namespace", namespace);
            gen.writeStringField("database", database);
            gen.writeStringField("version", "1.0");
            gen.writeEndObject();

            // Write tables
            gen.writeFieldName("tables");
            gen.writeStartObject();

            // Get all tables
            List<String> tables = getAllTables().block();

            if (tables != null) {
                for (String table : tables) {
                    log.debug("📦 Backing up table: {}", table);

                    gen.writeFieldName(table);
                    gen.writeStartArray();

                    // Stream records from table
                    streamTableRecords(table, gen).block();

                    gen.writeEndArray();
                }
            }

            gen.writeEndObject();
            gen.writeEndObject();
        }
    }

    /**
     * Read backup data using streaming JSON.
     */
    private void readBackupData(InputStream in, boolean dropExisting) throws IOException {
        JsonFactory factory = new JsonFactory(objectMapper);

        try (JsonParser parser = factory.createParser(in)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected START_OBJECT");
            }

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();

                if ("metadata".equals(fieldName)) {
                    parser.nextToken();
                    parser.skipChildren();
                } else if ("tables".equals(fieldName)) {
                    parser.nextToken(); // START_OBJECT
                    restoreTables(parser, dropExisting);
                }
            }
        }
    }

    /**
     * Restore tables from JSON parser.
     */
    private void restoreTables(JsonParser parser, boolean dropExisting) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String tableName = parser.currentName();
            parser.nextToken(); // START_ARRAY

            log.debug("📥 Restoring table: {}", tableName);

            if (dropExisting) {
                dropTable(tableName).block();
            }

            List<Map<String, Object>> batch = new ArrayList<>();
            int count = 0;

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                @SuppressWarnings("unchecked")
                Map<String, Object> record = objectMapper.readValue(parser, Map.class);
                batch.add(record);
                count++;

                // Insert in batches of 100
                if (batch.size() >= 100) {
                    insertBatch(tableName, batch).block();
                    batch.clear();
                }
            }

            // Insert remaining
            if (!batch.isEmpty()) {
                insertBatch(tableName, batch).block();
            }

            log.debug("✅ Restored {} records to table: {}", count, tableName);
        }
    }

    /**
     * Get all tables in the database.
     */
    private Mono<List<String>> getAllTables() {
        String sql = "SELECT name FROM sys::tables";

        return client.query(sql, Map.class)
            .map(result -> (String) result.get("name"))
            .collectList()
            .doOnError(e -> log.error("Failed to get tables", e));
    }

    /**
     * Stream all records from a table.
     */
    private Mono<Void> streamTableRecords(String table, JsonGenerator gen) {
        String sql = "SELECT * FROM " + table;

        return client.query(sql, Map.class)
            .doOnNext(record -> {
                try {
                    objectMapper.writeValue(gen, record);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write record", e);
                }
            })
            .then()
            .doOnError(e -> log.error("Failed to stream table: {}", table, e));
    }

    /**
     * Drop a table.
     */
    private Mono<Void> dropTable(String table) {
        String sql = "REMOVE TABLE " + table;

        return client.query(sql, Void.class)
            .then()
            .doOnSuccess(v -> log.debug("🗑️ Dropped table: {}", table))
            .doOnError(e -> log.warn("Failed to drop table: {}", table, e))
            .onErrorResume(e -> Mono.empty()); // Continue if drop fails
    }

    /**
     * Insert batch of records.
     */
    private Mono<Void> insertBatch(String table, List<Map<String, Object>> batch) {
        return Flux.fromIterable(batch)
            .flatMap(record -> {
                try {
                    String json = objectMapper.writeValueAsString(record);
                    String sql = "CREATE " + table + " CONTENT " + json;
                    return client.query(sql, Void.class);
                } catch (Exception e) {
                    log.warn("Failed to insert record into {}: {}", table, e.getMessage());
                    return Mono.empty();
                }
            })
            .then();
    }

    /**
     * Get backup statistics.
     */
    public Mono<BackupStats> getBackupStats(File backupFile) {
        return Mono.fromCallable(() -> {
            try (FileInputStream fis = new FileInputStream(backupFile);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 DataInputStream dis = new DataInputStream(bis)) {

                BackupHeader header = BackupHeader.readFrom(dis);
                long fileSize = backupFile.length();

                return new BackupStats(
                    backupFile.getName(),
                    fileSize,
                    header.version(),
                    header.timestampAsInstant(),
                    namespace,
                    database
                );

            } catch (IOException e) {
                throw new RuntimeException("Failed to read backup stats", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Backup statistics record.
     */
    public record BackupStats(
        String filename,
        long sizeBytes,
        byte version,
        java.time.Instant timestamp,
        String namespace,
        String database
    ) {
        public long sizeKB() {
            return sizeBytes / 1024;
        }

        public long sizeMB() {
            return sizeBytes / (1024 * 1024);
        }
    }
}

