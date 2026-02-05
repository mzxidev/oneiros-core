package io.oneiros.annotation;

import java.lang.annotation.*;

/**
 * Annotation to enable automatic versioning (time-travel/history tracking).
 * When enabled, creates a separate history table and tracks all changes.
 *
 * <p>Example:
 * <pre>
 * {@code
 * @OneirosVersioned(historyTable = "user_history", maxVersions = 10)
 * @OneirosEntity("users")
 * public class User { ... }
 * }
 * </pre>
 *
 * <p>This will:
 * <ul>
 *   <li>Create a "user_history" table</li>
 *   <li>Store old versions before updates</li>
 *   <li>Track version timestamp and change metadata</li>
 *   <li>Optionally limit number of versions per record</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OneirosVersioned {

    /**
     * Name of the history table.
     * If empty, defaults to "{tableName}_history"
     */
    String historyTable() default "";

    /**
     * Maximum number of versions to keep per record.
     * 0 = unlimited (default)
     */
    int maxVersions() default 0;

    /**
     * If true, includes metadata (user, timestamp, change type) in history.
     */
    boolean includeMetadata() default true;

    /**
     * If true, stores full record snapshots instead of diffs.
     */
    boolean fullSnapshots() default true;
}
