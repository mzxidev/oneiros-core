package io.oneiros.annotation;

import java.lang.annotation.*;

/**
 * Annotation to define graph relations (record links).
 * Generates appropriate ASSERT constraints for type checking.
 *
 * <p>Example:
 * <pre>
 * {@code
 * @OneirosRelation(target = User.class, type = RelationType.ONE_TO_MANY)
 * private List<String> friends;  // Will store user:* record IDs
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OneirosRelation {

    /**
     * Target entity class for the relation.
     */
    Class<?> target();

    /**
     * Type of relation.
     */
    RelationType type() default RelationType.ONE_TO_ONE;

    /**
     * If true, generates bidirectional references (REFERENCES clause).
     * Available since SurrealDB 2.2.0
     */
    boolean bidirectional() default false;

    /**
     * On delete behavior: "CASCADE", "RESTRICT", "SET NULL"
     */
    String onDelete() default "";

    enum RelationType {
        ONE_TO_ONE,      // Single record<target>
        ONE_TO_MANY,     // array<record<target>>
        MANY_TO_MANY     // array<record<target>> with junction table
    }
}
