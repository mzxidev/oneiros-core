package io.oneiros.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.oneiros.client.OneirosClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for OneirosBackupManager with LZ4 compression.
 */
class OneirosBackupManagerTest {

    @Mock
    private OneirosClient client;

    private ObjectMapper objectMapper;
    private OneirosBackupManager backupManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        backupManager = new OneirosBackupManager(client, objectMapper, "test_ns", "test_db");
    }

    @Test
    void shouldCreateBackupSuccessfully() {
        // Mock: Get tables
        when(client.query(eq("SELECT name FROM sys::tables"), eq(Map.class)))
            .thenReturn(Flux.fromIterable(List.of(
                Map.of("name", "users"),
                Map.of("name", "products")
            )));

        // Mock: Get records from 'users'
        when(client.query(eq("SELECT * FROM users"), eq(Map.class)))
            .thenReturn(Flux.fromIterable(List.of(
                Map.of("id", "user:1", "name", "Alice", "age", 30),
                Map.of("id", "user:2", "name", "Bob", "age", 25)
            )));

        // Mock: Get records from 'products'
        when(client.query(eq("SELECT * FROM products"), eq(Map.class)))
            .thenReturn(Flux.fromIterable(List.of(
                Map.of("id", "prod:1", "name", "Laptop", "price", 999.99),
                Map.of("id", "prod:2", "name", "Mouse", "price", 29.99)
            )));

        // Create backup
        StepVerifier.create(backupManager.createBackup(tempDir))
            .assertNext(backupFile -> {
                assertThat(backupFile).exists();
                assertThat(backupFile.getName()).startsWith("oneiros_backup_test_ns_test_db_");
                assertThat(backupFile.getName()).endsWith(".onb");
                assertThat(backupFile.length()).isGreaterThan(0);
            })
            .verifyComplete();
    }

    @Test
    void shouldRestoreBackupSuccessfully() {
        // First, create a backup
        when(client.query(eq("SELECT name FROM sys::tables"), eq(Map.class)))
            .thenReturn(Flux.fromIterable(List.of(Map.of("name", "users"))));

        when(client.query(eq("SELECT * FROM users"), eq(Map.class)))
            .thenReturn(Flux.fromIterable(List.of(
                Map.of("id", "user:1", "name", "Alice")
            )));

        File backupFile = backupManager.createBackup(tempDir).block();
        assertThat(backupFile).isNotNull();

        // Mock restore operations
        when(client.query(startsWith("CREATE users CONTENT"), eq(Void.class)))
            .thenReturn(Flux.empty());

        // Restore backup
        StepVerifier.create(backupManager.restoreBackup(backupFile, false))
            .verifyComplete();
    }

    @Test
    void shouldGetBackupStats() {
        // Create backup
        when(client.query(eq("SELECT name FROM sys::tables"), eq(Map.class)))
            .thenReturn(Flux.fromIterable(List.of(Map.of("name", "test"))));

        when(client.query(eq("SELECT * FROM test"), eq(Map.class)))
            .thenReturn(Flux.fromIterable(List.of(Map.of("id", "1", "value", "test"))));

        File backupFile = backupManager.createBackup(tempDir).block();
        assertThat(backupFile).isNotNull();

        // Get stats
        StepVerifier.create(backupManager.getBackupStats(backupFile))
            .assertNext(stats -> {
                assertThat(stats.filename()).isEqualTo(backupFile.getName());
                assertThat(stats.sizeBytes()).isEqualTo(backupFile.length());
                assertThat(stats.version()).isEqualTo((byte) 1);
                assertThat(stats.namespace()).isEqualTo("test_ns");
                assertThat(stats.database()).isEqualTo("test_db");
                assertThat(stats.timestamp()).isNotNull();
            })
            .verifyComplete();
    }

    @Test
    void shouldHandleEmptyDatabase() {
        // Mock: No tables
        when(client.query(eq("SELECT name FROM sys::tables"), eq(Map.class)))
            .thenReturn(Flux.empty());

        StepVerifier.create(backupManager.createBackup(tempDir))
            .assertNext(backupFile -> {
                assertThat(backupFile).exists();
                assertThat(backupFile.length()).isGreaterThan(0);
            })
            .verifyComplete();
    }

    @Test
    void shouldHandleTableWithNoRecords() {
        when(client.query(eq("SELECT name FROM sys::tables"), eq(Map.class)))
            .thenReturn(Flux.just(Map.of("name", "empty_table")));

        when(client.query(eq("SELECT * FROM empty_table"), eq(Map.class)))
            .thenReturn(Flux.empty());

        StepVerifier.create(backupManager.createBackup(tempDir))
            .assertNext(backupFile -> {
                assertThat(backupFile).exists();
            })
            .verifyComplete();
    }

    @Test
    void shouldCompressLargeBackup() {
        // Create a large dataset
        List<Map<String, Object>> largeDataset = generateLargeDataset(1000);

        when(client.query(eq("SELECT name FROM sys::tables"), eq(Map.class)))
            .thenReturn(Flux.just(Map.of("name", "large_table")));

        when(client.query(eq("SELECT * FROM large_table"), eq(Map.class)))
            .thenReturn(Flux.fromIterable(largeDataset));

        StepVerifier.create(backupManager.createBackup(tempDir))
            .assertNext(backupFile -> {
                assertThat(backupFile).exists();
                assertThat(backupFile.length()).isGreaterThan(100); // Should have data
            })
            .verifyComplete();
    }

    @Test
    void shouldRestoreWithDropExisting() {
        // Create backup
        when(client.query(eq("SELECT name FROM sys::tables"), eq(Map.class)))
            .thenReturn(Flux.just(Map.of("name", "users")));

        when(client.query(eq("SELECT * FROM users"), eq(Map.class)))
            .thenReturn(Flux.just(Map.of("id", "user:1", "name", "Alice")));

        File backupFile = backupManager.createBackup(tempDir).block();
        assertThat(backupFile).isNotNull();

        // Mock drop and restore
        when(client.query(eq("REMOVE TABLE users"), eq(Void.class)))
            .thenReturn(Flux.empty());

        when(client.query(startsWith("CREATE users CONTENT"), eq(Void.class)))
            .thenReturn(Flux.empty());

        // Restore with drop
        StepVerifier.create(backupManager.restoreBackup(backupFile, true))
            .verifyComplete();
    }

    @Test
    void shouldBatchInsertDuringRestore() {
        // Create backup with many records
        List<Map<String, Object>> manyRecords = generateLargeDataset(250);

        when(client.query(eq("SELECT name FROM sys::tables"), eq(Map.class)))
            .thenReturn(Flux.just(Map.of("name", "batch_table")));

        when(client.query(eq("SELECT * FROM batch_table"), eq(Map.class)))
            .thenReturn(Flux.fromIterable(manyRecords));

        File backupFile = backupManager.createBackup(tempDir).block();
        assertThat(backupFile).isNotNull();

        // Mock batch inserts
        when(client.query(startsWith("CREATE batch_table CONTENT"), eq(Void.class)))
            .thenReturn(Flux.empty());

        StepVerifier.create(backupManager.restoreBackup(backupFile, false))
            .verifyComplete();
    }

    private List<Map<String, Object>> generateLargeDataset(int size) {
        return java.util.stream.IntStream.range(0, size)
            .mapToObj(i -> Map.<String, Object>of(
                "id", "record:" + i,
                "name", "Name_" + i,
                "value", i * 100,
                "description", "This is a test record number " + i
            ))
            .toList();
    }
}

