package io.oneiros.backup;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oneiros.client.OneirosClient;
import io.oneiros.security.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
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
 * - SHA-256 integrity checks (v0.4.4+)
 * - Optional AES-256-GCM encryption (v0.4.4+)
 */
public class OneirosBackupManager {
    private static final Logger log = LoggerFactory.getLogger(OneirosBackupManager.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final OneirosClient client;
    private final ObjectMapper objectMapper;
    private final String namespace;
    private final String database;
    private final CryptoService cryptoService; // Optional encryption support

    public OneirosBackupManager(OneirosClient client, ObjectMapper objectMapper, String namespace, String database) {
        this(client, objectMapper, namespace, database, null);
    }

    public OneirosBackupManager(OneirosClient client, ObjectMapper objectMapper, String namespace, String database, CryptoService cryptoService) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.namespace = namespace;
        this.database = database;
        this.cryptoService = cryptoService;
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

            // SECURITY FIX: Sanitize namespace and database to prevent path traversal
            String sanitizedNamespace = sanitizeFilenameComponent(namespace);
            String sanitizedDatabase = sanitizeFilenameComponent(database);

            // Create backup file
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String filename = String.format("oneiros_backup_%s_%s_%s.onb",
                sanitizedNamespace, sanitizedDatabase, timestamp);
            File backupFile = directory.resolve(filename).toFile();

            // SECURITY: Verify canonical path to prevent directory traversal
            File canonicalBackupFile = backupFile.getCanonicalFile();
            File canonicalDir = directory.toFile().getCanonicalFile();
            if (!canonicalBackupFile.getParentFile().equals(canonicalDir)) {
                throw new SecurityException(
                    "Path traversal attempt detected: backup file must be in target directory"
                );
            }

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

            // SECURITY: Generate SHA-256 checksum for integrity verification
            String checksum = generateChecksum(backupFile);
            File checksumFile = new File(backupFile.getAbsolutePath() + ".sha256");
            Files.writeString(checksumFile.toPath(), checksum);
            log.info("🔒 Integrity checksum saved: {}", checksumFile.getName());

            return backupFile;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Create an encrypted backup (AES-256-GCM).
     *
     * <p>Process: Data → JSON → LZ4 Compress → AES-256-GCM Encrypt → File
     *
     * @param directory Directory to save backup
     * @return Path to created encrypted backup file
     * @throws IllegalStateException if CryptoService is not configured
     */
    public Mono<File> createEncryptedBackup(Path directory) {
        if (cryptoService == null || !cryptoService.isEnabled()) {
            return Mono.error(new IllegalStateException(
                "CryptoService not configured or disabled. Cannot create encrypted backup."
            ));
        }

        return Mono.fromCallable(() -> {
            log.info("🔐 Starting encrypted backup: namespace={}, database={}", namespace, database);

            // SECURITY: Sanitize namespace and database to prevent path traversal
            String sanitizedNamespace = sanitizeFilenameComponent(namespace);
            String sanitizedDatabase = sanitizeFilenameComponent(database);

            // Create encrypted backup file
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String filename = String.format("oneiros_backup_%s_%s_%s.onb.enc",
                sanitizedNamespace, sanitizedDatabase, timestamp);
            File encryptedBackupFile = directory.resolve(filename).toFile();

            // SECURITY: Verify canonical path to prevent directory traversal
            File canonicalBackupFile = encryptedBackupFile.getCanonicalFile();
            File canonicalDir = directory.toFile().getCanonicalFile();
            if (!canonicalBackupFile.getParentFile().equals(canonicalDir)) {
                throw new SecurityException(
                    "Path traversal attempt detected: backup file must be in target directory"
                );
            }

            Files.createDirectories(directory);

            // Create temporary unencrypted file
            File tempBackupFile = File.createTempFile("oneiros_backup_", ".tmp");
            try {
                // Write compressed backup to temp file
                try (FileOutputStream fos = new FileOutputStream(tempBackupFile);
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

                // Encrypt the entire backup file
                byte[] backupBytes = Files.readAllBytes(tempBackupFile.toPath());
                String base64Backup = Base64.getEncoder().encodeToString(backupBytes);
                String encryptedData = cryptoService.encrypt(base64Backup);

                // Write encrypted data
                Files.writeString(encryptedBackupFile.toPath(), encryptedData);

                // SECURITY: Generate SHA-256 checksum for integrity verification
                String checksum = generateChecksum(encryptedBackupFile);
                File checksumFile = new File(encryptedBackupFile.getAbsolutePath() + ".sha256");
                Files.writeString(checksumFile.toPath(), checksum);

                long sizeKB = encryptedBackupFile.length() / 1024;
                log.info("✅ Encrypted backup created: {} ({} KB)", encryptedBackupFile.getName(), sizeKB);
                log.info("🔒 Integrity checksum saved: {}", checksumFile.getName());

                return encryptedBackupFile;
            } finally {
                // Clean up temp file
                if (tempBackupFile.exists()) {
                    tempBackupFile.delete();
                }
            }
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

            // SECURITY: Verify integrity checksum before restore
            File checksumFile = new File(backupFile.getAbsolutePath() + ".sha256");
            if (checksumFile.exists()) {
                try {
                    verifyBackupIntegrity(backupFile);
                    log.info("✅ Integrity check passed");
                } catch (Exception e) {
                    throw new SecurityException("Backup integrity check failed: " + e.getMessage(), e);
                }
            } else {
                log.warn("⚠️ No checksum file found - skipping integrity check");
            }

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
     * Restore from an encrypted backup (AES-256-GCM).
     *
     * <p>Process: File → AES-256-GCM Decrypt → LZ4 Decompress → JSON → Data
     *
     * @param encryptedBackupFile Encrypted backup file to restore
     * @param dropExisting Whether to drop existing tables before restore
     * @throws IllegalStateException if CryptoService is not configured
     */
    public Mono<Void> restoreEncryptedBackup(File encryptedBackupFile, boolean dropExisting) {
        if (cryptoService == null || !cryptoService.isEnabled()) {
            return Mono.error(new IllegalStateException(
                "CryptoService not configured or disabled. Cannot restore encrypted backup."
            ));
        }

        return Mono.fromRunnable(() -> {
            log.info("🔐 Starting encrypted restore from: {}", encryptedBackupFile.getName());

            // SECURITY: Verify integrity checksum before restore
            File checksumFile = new File(encryptedBackupFile.getAbsolutePath() + ".sha256");
            if (checksumFile.exists()) {
                try {
                    verifyBackupIntegrity(encryptedBackupFile);
                    log.info("✅ Integrity check passed");
                } catch (Exception e) {
                    throw new SecurityException("Encrypted backup integrity check failed: " + e.getMessage(), e);
                }
            } else {
                log.warn("⚠️ No checksum file found - skipping integrity check");
            }

            // Decrypt and restore
            File tempBackupFile = null;
            try {
                // Read and decrypt
                String encryptedData = Files.readString(encryptedBackupFile.toPath());
                String base64Backup = cryptoService.decrypt(encryptedData);
                byte[] backupBytes = Base64.getDecoder().decode(base64Backup);

                // Write decrypted backup to temp file
                tempBackupFile = File.createTempFile("oneiros_restore_", ".tmp");
                Files.write(tempBackupFile.toPath(), backupBytes);

                // Restore from temp file
                try (FileInputStream fis = new FileInputStream(tempBackupFile);
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
                }

                log.info("✅ Encrypted restore completed");

            } catch (IOException e) {
                throw new RuntimeException("Failed to restore encrypted backup", e);
            } finally {
                // Clean up temp file
                if (tempBackupFile != null && tempBackupFile.exists()) {
                    tempBackupFile.delete();
                }
            }
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

    /**
     * SECURITY: Verifies backup file integrity using SHA-256 checksum.
     *
     * @param backupFile the backup file to verify
     * @throws IOException if checksum file cannot be read
     * @throws SecurityException if checksums don't match
     */
    public void verifyBackupIntegrity(File backupFile) throws IOException {
        File checksumFile = new File(backupFile.getAbsolutePath() + ".sha256");

        if (!checksumFile.exists()) {
            throw new IOException("Checksum file not found: " + checksumFile.getName());
        }

        String expectedChecksum = Files.readString(checksumFile.toPath()).trim();
        String actualChecksum = generateChecksum(backupFile);

        if (!actualChecksum.equals(expectedChecksum)) {
            throw new SecurityException(String.format(
                "Backup integrity check failed! Expected: %s, Got: %s. " +
                "File may have been corrupted or tampered with.",
                expectedChecksum, actualChecksum
            ));
        }
    }

    /**
     * SECURITY: Generates SHA-256 checksum for a file.
     *
     * @param file the file to hash
     * @return Base64-encoded SHA-256 checksum
     * @throws IOException if file cannot be read
     */
    private String generateChecksum(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            byte[] hash = digest.digest(fileBytes);
            return Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * SECURITY: Sanitizes a filename component to prevent path traversal attacks.
     *
     * <p>Only allows alphanumeric characters, underscores, and hyphens.
     * Blocks: path separators (/, \), parent directory (..), null bytes, etc.
     *
     * @param component the filename component (e.g., namespace, database name)
     * @return sanitized component
     * @throws SecurityException if component contains invalid characters
     */
    private static String sanitizeFilenameComponent(String component) {
        if (component == null || component.isEmpty()) {
            throw new IllegalArgumentException("Filename component cannot be null or empty");
        }

        // Check for path traversal attempts
        if (component.contains("..") || component.contains("/") || component.contains("\\")) {
            throw new SecurityException(
                "Path traversal characters detected in filename component: " + component
            );
        }

        // Check for null bytes (used in path traversal attacks)
        if (component.contains("\0")) {
            throw new SecurityException("Null byte detected in filename component");
        }

        // Only allow safe characters: alphanumeric, underscore, hyphen
        if (!component.matches("^[a-zA-Z0-9_-]+$")) {
            throw new SecurityException(
                "Invalid characters in filename component (allowed: a-zA-Z0-9_-): " + component
            );
        }

        // Prevent excessively long filenames
        if (component.length() > 64) {
            throw new SecurityException(
                "Filename component too long (max 64 characters): " + component
            );
        }

        return component;
    }
}

