package io.oneiros.example.schema;

import io.oneiros.annotation.*;
import io.oneiros.security.EncryptionType;

/**
 * Schema definition for the 'users' table in SurrealDB.
 *
 * This is a dedicated schema class that defines the table structure
 * without being an entity itself (Separation of Concerns pattern).
 *
 * Benefits:
 * - Clean separation between data model (Entity) and schema definition
 * - Can define tables without creating entity classes
 * - Useful for shared/system tables
 * - Better control over migrations
 */
@OneirosTable(
    value = "users",
    schemafull = true,
    comment = "User accounts with authentication",
    permissions = "FOR select, update WHERE id = $auth.id OR $auth.role = 'admin'",
    dropOnStart = false // Set to true in dev mode to reset table
)
public class UsersSchema {

    @OneirosField(
        type = "string",
        assertion = "string::len($value) >= 3",
        comment = "Unique username",
        unique = true,
        index = true
    )
    private String username;

    @OneirosField(
        type = "string",
        assertion = "string::len($value) >= 5",
        comment = "User email address",
        unique = true,
        index = true
    )
    private String email;

    @OneirosField(
        type = "string",
        comment = "Encrypted password hash"
    )
    @OneirosEncrypted(type = EncryptionType.ARGON2)
    private String password;

    @OneirosField(
        type = "string",
        defaultValue = "USER",
        comment = "User role: USER, VENDOR, ADMIN"
    )
    private String role;

    @OneirosField(
        type = "bool",
        defaultValue = "false"
    )
    private boolean emailVerified;

    @OneirosField(
        type = "datetime",
        defaultValue = "time::now()",
        readonly = true
    )
    private java.time.Instant createdAt;

    // Note: This class has no getters/setters - it's schema-only!
}

