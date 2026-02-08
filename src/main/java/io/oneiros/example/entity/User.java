package io.oneiros.example.entity;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.annotation.OneirosID;
import lombok.Data;

import java.time.Instant;

/**
 * User entity for working with user data.
 *
 * This is the data model class - it contains business logic, getters/setters,
 * and is used for CRUD operations.
 *
 * The table schema is defined in {@link io.oneiros.example.schema.UsersSchema}.
 *
 * Benefits of this separation:
 * - Entity class is clean and focuses on business logic
 * - Schema class focuses on database structure and constraints
 * - Can update schema without touching entity code
 * - Can have multiple entities for the same table (e.g., UserPublicView, UserAdminView)
 */
@Data
@OneirosEntity("users")
public class User {

    @OneirosID
    private String id;

    private String username;
    private String email;

    // Password is encrypted via @OneirosEncrypted in schema
    private String password;

    private String role;
    private boolean emailVerified;
    private Instant createdAt;

    // Business logic methods
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isVerified() {
        return emailVerified;
    }

    public String getDisplayName() {
        return username != null ? username : email;
    }
}

