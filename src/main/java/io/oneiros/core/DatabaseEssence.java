package io.oneiros.core;

/**
 * Specialized essence for database connectivity.
 */
public interface DatabaseEssence extends Essence {
    
    /**
     * Gets a session or client instance for this database.
     */
    <T> T getSession();
    
    /**
     * Executes database-specific migrations.
     */
    default void executeMigration() {}
}
