package io.oneiros.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oneiros.backup.OneirosBackupManager;
import io.oneiros.backup.OneirosBackupManager.BackupStats;
import io.oneiros.core.Oneiros;
import io.oneiros.core.OneirosBuilder;

import java.io.File;
import java.nio.file.Path;

/**
 * Example: Using Oneiros Backup System (Pure Java, no framework).
 */
public class BackupExample {

    public static void main(String[] args) {
        // 1. Create Oneiros instance
        Oneiros oneiros = OneirosBuilder.create()
            .url("ws://localhost:8000/rpc")
            .namespace("myns")
            .database("mydb")
            .username("root")
            .password("root")
            .build();

        try {
            // 2. Create backup manager
            OneirosBackupManager backupManager = new OneirosBackupManager(
                oneiros.client(),
                new ObjectMapper(),
                "myns",
                "mydb"
            );

            // 3. Create backup
            System.out.println("📦 Creating backup...");
            File backup = backupManager.createBackup(Path.of("./backups"))
                .block();

            System.out.println("✅ Backup created: " + backup.getName());

            // 4. Get backup stats
            BackupStats stats = backupManager.getBackupStats(backup).block();

            if (stats != null) {
                System.out.println("\n📊 Backup Statistics:");
                System.out.println("   Filename: " + stats.filename());
                System.out.println("   Size: " + stats.sizeKB() + " KB");
                System.out.println("   Timestamp: " + stats.timestamp());
                System.out.println("   Namespace: " + stats.namespace());
                System.out.println("   Database: " + stats.database());
            }

            // 5. Optional: Restore backup
            // System.out.println("\n📥 Restoring backup...");
            // backupManager.restoreBackup(backup, true).block();
            // System.out.println("✅ Restore completed!");

        } finally {
            // 6. Cleanup
            oneiros.close();
        }
    }
}

