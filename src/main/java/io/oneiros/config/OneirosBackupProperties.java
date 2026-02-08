package io.oneiros.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Oneiros Backup.
 */
@ConfigurationProperties(prefix = "oneiros.backup")
public class OneirosBackupProperties {

    /**
     * Enable backup functionality.
     */
    private boolean enabled = false;

    /**
     * Directory to store backups.
     */
    private String directory = "./backups";

    /**
     * Schedule configuration.
     */
    private Schedule schedule = new Schedule();

    /**
     * Retention policy.
     */
    private Retention retention = new Retention();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public Retention getRetention() {
        return retention;
    }

    public void setRetention(Retention retention) {
        this.retention = retention;
    }

    /**
     * Schedule configuration.
     */
    public static class Schedule {

        /**
         * Enable scheduled backups.
         */
        private boolean enabled = false;

        /**
         * Cron expression for backup schedule.
         * Default: Daily at 2 AM
         */
        private String cron = "0 0 2 * * *";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    /**
     * Retention policy configuration.
     */
    public static class Retention {

        /**
         * Maximum number of backups to keep.
         * Older backups will be deleted automatically.
         */
        private Integer maxBackups = 7;

        /**
         * Maximum age of backups in days.
         * Backups older than this will be deleted.
         */
        private Integer maxAgeDays;

        public Integer getMaxBackups() {
            return maxBackups;
        }

        public void setMaxBackups(Integer maxBackups) {
            this.maxBackups = maxBackups;
        }

        public Integer getMaxAgeDays() {
            return maxAgeDays;
        }

        public void setMaxAgeDays(Integer maxAgeDays) {
            this.maxAgeDays = maxAgeDays;
        }
    }
}

