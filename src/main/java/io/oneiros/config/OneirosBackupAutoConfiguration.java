package io.oneiros.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oneiros.backup.OneirosBackupManager;
import io.oneiros.client.OneirosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Spring Boot auto-configuration for Oneiros Backup.
 *
 * Enable via:
 * <pre>
 * oneiros:
 *   backup:
 *     enabled: true
 *     directory: ./backups
 *     schedule:
 *       enabled: true
 *       cron: "0 0 2 * * *"  # Daily at 2 AM
 *     retention:
 *       maxBackups: 7  # Keep last 7 backups
 * </pre>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "oneiros.backup", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OneirosBackupProperties.class)
@EnableScheduling
public class OneirosBackupAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(OneirosBackupAutoConfiguration.class);

    @Bean
    public OneirosBackupManager oneirosBackupManager(
            OneirosClient client,
            ObjectMapper objectMapper,
            OneirosProperties properties) {

        String namespace = properties.getNamespace();
        String database = properties.getDatabase();

        log.info("🗄️ Initializing Oneiros Backup Manager");
        log.info("   📦 Namespace: {}", namespace);
        log.info("   📦 Database: {}", database);

        return new OneirosBackupManager(client, objectMapper, namespace, database);
    }

    @Bean
    @ConditionalOnProperty(prefix = "oneiros.backup.schedule", name = "enabled", havingValue = "true")
    public BackupScheduler backupScheduler(
            OneirosBackupManager backupManager,
            OneirosBackupProperties backupProperties) {

        log.info("⏰ Enabling automatic backup scheduler");
        log.info("   📅 Cron: {}", backupProperties.getSchedule().getCron());
        log.info("   📂 Directory: {}", backupProperties.getDirectory());

        return new BackupScheduler(backupManager, backupProperties);
    }

    /**
     * Scheduled backup executor.
     */
    public static class BackupScheduler {
        private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

        private final OneirosBackupManager backupManager;
        private final OneirosBackupProperties properties;

        public BackupScheduler(OneirosBackupManager backupManager, OneirosBackupProperties properties) {
            this.backupManager = backupManager;
            this.properties = properties;
        }

        @Scheduled(cron = "${oneiros.backup.schedule.cron:0 0 2 * * *}")
        public void performScheduledBackup() {
            log.info("⏰ Starting scheduled backup...");

            try {
                Path directory = Paths.get(properties.getDirectory());

                File backupFile = backupManager.createBackup(directory).block();

                if (backupFile != null) {
                    log.info("✅ Scheduled backup completed: {}", backupFile.getName());

                    // Cleanup old backups if retention is configured
                    if (properties.getRetention() != null) {
                        cleanupOldBackups(directory);
                    }
                }

            } catch (Exception e) {
                log.error("❌ Scheduled backup failed", e);
            }
        }

        /**
         * Cleanup old backups based on retention policy.
         */
        private void cleanupOldBackups(Path directory) {
            try {
                Integer maxBackups = properties.getRetention().getMaxBackups();

                if (maxBackups == null || maxBackups <= 0) {
                    return;
                }

                File[] backups = directory.toFile().listFiles((dir, name) ->
                    name.startsWith("oneiros_backup_") && name.endsWith(".onb")
                );

                if (backups == null || backups.length <= maxBackups) {
                    return;
                }

                // Sort by last modified (oldest first)
                java.util.Arrays.sort(backups, (a, b) ->
                    Long.compare(a.lastModified(), b.lastModified())
                );

                // Delete oldest backups
                int toDelete = backups.length - maxBackups;

                for (int i = 0; i < toDelete; i++) {
                    if (backups[i].delete()) {
                        log.info("🗑️ Deleted old backup: {}", backups[i].getName());
                    }
                }

            } catch (Exception e) {
                log.warn("Failed to cleanup old backups", e);
            }
        }
    }
}

