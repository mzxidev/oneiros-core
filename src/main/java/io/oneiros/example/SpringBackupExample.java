package io.oneiros.example;

import io.oneiros.backup.OneirosBackupManager;
import io.oneiros.backup.OneirosBackupManager.BackupStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Example: Using Oneiros Backup System with Spring Boot.
 *
 * <p>This example demonstrates:
 * <ul>
 *   <li>Auto-configured backup manager via Spring Boot</li>
 *   <li>Manual backup creation</li>
 *   <li>Backup statistics retrieval</li>
 *   <li>Backup restoration</li>
 * </ul>
 *
 * <p>Configuration in application.yml:
 * <pre>
 * oneiros:
 *   url: ws://localhost:8000/rpc
 *   namespace: myns
 *   database: mydb
 *   username: root
 *   password: root
 *   backup:
 *     enabled: true
 *     directory: ./backups
 *     schedule:
 *       enabled: true
 *       cron: "0 0 2 * * *"  # Daily at 2 AM
 *     retention:
 *       max-backups: 7
 *       max-age-days: 30
 * </pre>
 */
@SpringBootApplication
public class SpringBackupExample {

    public static void main(String[] args) {
        SpringApplication.run(SpringBackupExample.class, args);
    }

    @Component
    static class BackupRunner implements CommandLineRunner {
        private static final Logger log = LoggerFactory.getLogger(BackupRunner.class);

        private final OneirosBackupManager backupManager;

        public BackupRunner(OneirosBackupManager backupManager) {
            this.backupManager = backupManager;
        }

        @Override
        public void run(String... args) {
            log.info("🚀 Starting Backup Example...");

            // Example 1: Create a backup
            createBackup()
                // Example 2: Get backup statistics
                .flatMap(this::getBackupStats)
                // Example 3: Optional - Restore backup
                // .flatMap(backup -> restoreBackup(backup, false))
                .doOnSuccess(v -> log.info("✅ Backup example completed!"))
                .doOnError(e -> log.error("❌ Backup example failed", e))
                .block();
        }

        /**
         * Create a backup.
         */
        private Mono<File> createBackup() {
            log.info("📦 Creating backup...");

            return backupManager.createBackup(Paths.get("./backups"))
                .doOnSuccess(backup -> {
                    log.info("✅ Backup created: {}", backup.getName());
                    log.info("   Size: {} KB", backup.length() / 1024);
                });
        }

        /**
         * Get backup statistics.
         */
        private Mono<File> getBackupStats(File backup) {
            log.info("📊 Retrieving backup statistics...");

            return backupManager.getBackupStats(backup)
                .doOnSuccess(stats -> {
                    log.info("✅ Backup Statistics:");
                    log.info("   Filename: {}", stats.filename());
                    log.info("   Size: {} KB ({} MB)", stats.sizeKB(), stats.sizeMB());
                    log.info("   Timestamp: {}", stats.timestamp());
                    log.info("   Namespace: {}", stats.namespace());
                    log.info("   Database: {}", stats.database());
                })
                .thenReturn(backup);
        }

        /**
         * Restore a backup.
         *
         * @param backup The backup file to restore
         * @param dropExisting Whether to drop existing tables before restore
         */
        private Mono<Void> restoreBackup(File backup, boolean dropExisting) {
            log.info("📥 Restoring backup: {} (dropExisting={})", backup.getName(), dropExisting);

            return backupManager.restoreBackup(backup, dropExisting)
                .doOnSuccess(v -> log.info("✅ Backup restored successfully!"));
        }
    }
}

