package io.oneiros.annotation;

import java.lang.annotation.*;

/**
 * Annotation to define field-level configuration for SurrealDB.
 * Maps to SurrealDB's DEFINE FIELD statement.
 *
 * <p>Example:
 * <pre>
 * {@code
 * @OneirosField(type = "string", unique = true, index = true)
 * private String email;
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OneirosField {

    /**
     * SurrealDB type for this field.
     * Examples: "string", "int", "datetime", "array<string>", "record<user>"
     * If empty, type inference will be used.
     */
    String type() default "";

    /**
     * If true, creates a UNIQUE index for this field.
     */
    boolean unique() default false;

    /**
     * If true, creates a regular index for this field.
     */
    boolean index() default false;

    /**
     * Index name (optional). If not specified, auto-generated.
     */
    String indexName() default "";

    /**
     * Default value expression (SurrealQL).
     * Example: "time::now()", "'default_value'"
     */
    String defaultValue() default "";

    /**
     * If true, field is readonly after creation.
     */
    boolean readonly() default false;

    /**
     * Custom assertion (SurrealQL expression).
     *
     * <p><b>IMPORTANT:</b> Use SurrealDB functions, NOT JavaScript methods!
     * <ul>
     *   <li>✅ Correct: "string::len($value) > 5"</li>
     *   <li>❌ Wrong: "$value.length() > 5"</li>
     *   <li>✅ Correct: "$value > 0"</li>
     *   <li>✅ Correct: "string::is::email($value)"</li>
     * </ul>
     *
     * @see <a href="https://surrealdb.com/docs/surrealql/functions/string">SurrealDB String Functions</a>
     */
    String assertion() default "";

    /**
     * Optional comment for the field.
     */
    String comment() default "";

    /**
     * If true, the field is optional (nullable).
     * Generates SurrealDB type: option<type>
     *
     * <p>Example:
     * <pre>
     * {@code
     * @OneirosField(optional = true)
     * private String avatarUrl;  // Will be: TYPE option<string>
     * }
     * </pre>
     *
     * <p>Note: This is important for fields that may be null in Java.
     * SurrealDB will reject NONE values for non-optional fields.
     */
    boolean optional() default false;
}
