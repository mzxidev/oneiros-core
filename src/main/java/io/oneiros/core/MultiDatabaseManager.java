package io.oneiros.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager essence that routes between multiple database essences.
 */
public final class MultiDatabaseManager implements Essence {
    
    private final Map<String, DatabaseEssence> dbRoutes = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "core.multi_db";
    }

    /**
     * Registers a database essence with an alias.
     */
    public void addDatabase(String alias, DatabaseEssence essence) {
        dbRoutes.put(alias, essence);
    }

    /**
     * Retrieves a database essence by alias.
     */
    public DatabaseEssence db(String alias) {
        DatabaseEssence essence = dbRoutes.get(alias);
        if (essence == null) {
            throw new IllegalArgumentException("No database essence registered with alias: " + alias);
        }
        return essence;
    }

    /**
     * Convenience to get a session directly.
     */
    public <T> T session(String alias) {
        return db(alias).getSession();
    }
}
