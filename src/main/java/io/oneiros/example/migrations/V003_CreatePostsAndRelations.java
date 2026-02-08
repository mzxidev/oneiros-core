package io.oneiros.example.migrations;

import io.oneiros.client.OneirosClient;
import io.oneiros.migration.OneirosMigration;
import reactor.core.publisher.Mono;

/**
 * Example of a complex migration with graph relations (V003).
 *
 * <p>This migration creates a posts table and defines a relationship
 * between users and posts.
 */
public class V003_CreatePostsAndRelations implements OneirosMigration {

    @Override
    public int getVersion() {
        return 3;
    }

    @Override
    public String getDescription() {
        return "Create posts table and user->posts relation";
    }

    @Override
    public Mono<Void> up(OneirosClient client) {
        String sql = """
            -- Create posts table
            DEFINE TABLE IF NOT EXISTS post SCHEMAFULL;
            DEFINE FIELD IF NOT EXISTS title ON post TYPE string;
            DEFINE FIELD IF NOT EXISTS content ON post TYPE string;
            DEFINE FIELD IF NOT EXISTS author ON post TYPE record<user>;
            DEFINE FIELD IF NOT EXISTS created_at ON post TYPE datetime DEFAULT time::now();
            
            -- Create relation table for likes
            DEFINE TABLE IF NOT EXISTS likes SCHEMAFULL TYPE RELATION;
            DEFINE FIELD IF NOT EXISTS in ON likes TYPE record<post>;
            DEFINE FIELD IF NOT EXISTS out ON likes TYPE record<user>;
            DEFINE FIELD IF NOT EXISTS created_at ON likes TYPE datetime DEFAULT time::now();
            """;

        return client.query(sql, Object.class).then();
    }
}

