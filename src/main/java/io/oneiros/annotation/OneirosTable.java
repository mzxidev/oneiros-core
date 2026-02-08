package io.oneiros.annotation;

import java.lang.annotation.*;

/**
 * Annotation to define table-level configuration for SurrealDB.
 * Maps to SurrealDB's DEFINE TABLE statement.
 *
 * <p>This annotation can be used in two ways:
 * <ol>
 *   <li>On a dedicated Schema class (recommended for Separation of Concerns)</li>
 *   <li>On an Entity class (combined with @OneirosEntity)</li>
 * </ol>
 *
 * <p>Example (Dedicated Schema):
 * <pre>
 * {@code
 * @OneirosTable(value = "users", schemafull = true, permissions = "FOR select WHERE published = true")
 * public class UsersSchema {
 *     // Field definitions only
 * }
 * }
 * </pre>
 *
 * <p>Example (Combined with Entity):
 * <pre>
 * {@code
 * @OneirosTable(schemafull = true)
 * @OneirosEntity("users")
 * public class User {
 *     // Entity fields
 * }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OneirosTable {

    /**
     * Table name in SurrealDB.
     * If empty, uses the value from @OneirosEntity or lowercased class name.
     */
    String value() default "";

    /**
     * If true, generates SCHEMAFULL table (strict mode).
     * If false, generates SCHEMALESS table (flexible mode).
     * Default is false (SCHEMALESS).
     */
    boolean schemafull() default false;

    /**
     * Optional comment for the table definition.
     */
    String comment() default "";

    /**
     * Enable change feed for this table.
     * Duration format: "3d", "24h", "1w"
     */
    String changeFeed() default "";

    /**
     * Table type: "NORMAL", "RELATION"
     * Default is "NORMAL"
     */
    String type() default "NORMAL";

    /**
     * Table permissions in SurrealQL syntax.
     * Default: "FULL" (all operations allowed)
     *
     * Examples:
     * - "FULL" - All operations allowed
     * - "FOR select WHERE published = true" - Read-only for published records
     * - "FOR select, update WHERE user = $auth.id" - User can only modify own records
     * - "FOR create, select WHERE $auth NONE, FOR delete WHERE user = $auth.id" - Complex permissions
     */
    String permissions() default "FULL";

    /**
     * Drop the table before creating (useful for development).
     * WARNING: This will delete all data in the table!
     * Default: false
     */
    boolean dropOnStart() default false;
}
