package io.oneiros.annotation;

import java.lang.annotation.*;

/**
 * Annotation to define table-level configuration for SurrealDB.
 * Maps to SurrealDB's DEFINE TABLE statement.
 *
 * <p>Example:
 * <pre>
 * {@code
 * @OneirosTable(isStrict = true, comment = "User accounts")
 * @OneirosEntity("users")
 * public class User { ... }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OneirosTable {

    /**
     * If true, generates SCHEMAFULL table (strict mode).
     * If false, generates SCHEMALESS table (flexible mode).
     * Default is false (SCHEMALESS).
     */
    boolean isStrict() default false;

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
}
