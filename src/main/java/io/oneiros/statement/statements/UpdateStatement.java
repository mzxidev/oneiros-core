package io.oneiros.statement.statements;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import io.oneiros.statement.Statement;
import io.oneiros.statement.clause.WhereClause;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UPDATE statement for modifying existing records with SQL injection protection.
 *
 * <p>Supports:
 * <ul>
 *   <li>SET field = value syntax</li>
 *   <li>MERGE partial updates</li>
 *   <li>CONTENT full replacement</li>
 *   <li>WHERE conditions (parameterized by default)</li>
 *   <li>RETURN clauses</li>
 *   <li>TIMEOUT</li>
 * </ul>
 *
 * <p><b>🔐 Security:</b> All WHERE methods use parameterized queries by default.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * UpdateStatement.table(User.class)
 *     .set("name", "Bob")
 *     .where("id", "=", "user:123")  // Parameterized!
 *     .returnAfter()
 *     .execute(client);
 * }</pre>
 *
 * @param <T> the entity type
 * @since 1.0.0
 */
public class UpdateStatement<T> implements Statement<T> {

    private final Class<T> type;
    private final String target;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final WhereClause whereClause = new WhereClause();
    private String mergeContent;
    private String content;
    private String returnClause = "AFTER";
    private String timeoutClause;
    private boolean onlyOne = false;

    private UpdateStatement(Class<T> type, String target) {
        this.type = type;
        this.target = target;
    }

    /**
     * Start an UPDATE statement for a table.
     *
     * @param type the entity class
     * @return a new UPDATE statement
     */
    public static <T> UpdateStatement<T> table(Class<T> type) {
        return new UpdateStatement<>(type, getTableName(type));
    }

    /**
     * Start an UPDATE statement for a specific record ID.
     *
     * @param type the entity class
     * @param id   the record ID
     * @return a new UPDATE statement
     */
    public static <T> UpdateStatement<T> record(Class<T> type, String id) {
        return new UpdateStatement<>(type, getTableName(type) + ":" + id);
    }

    /**
     * Set a field value.
     *
     * @param field the field name
     * @param value the field value
     * @return this statement for chaining
     */
    public UpdateStatement<T> set(String field, Object value) {
        validateFieldName(field);
        this.fields.put(field, value);
        return this;
    }

    /**
     * Set a field using raw expression (e.g. "balance += 100").
     *
     * <p><b>⚠️ SECURITY WARNING:</b> This method is vulnerable to SQL injection
     * if user input is included in the expression. Only use with hardcoded expressions.
     *
     * <p>For increment/decrement operations with user input, use parameterized queries instead:
     * <pre>{@code
     * // ✅ SAFE: Hardcoded expression
     * .setRaw("balance += 100")
     *
     * // ❌ UNSAFE: User input in expression
     * .setRaw("balance += " + userInput)  // SQL Injection risk!
     *
     * // ✅ SAFE: Use parameterized query
     * client.query("UPDATE users SET balance += $amount WHERE id = $id",
     *     Map.of("amount", userInput, "id", id), User.class);
     * }</pre>
     *
     * @param expression the raw field expression (DO NOT include user input!)
     * @return this statement for chaining
     * @deprecated Consider using parameterized queries for dynamic values.
     */
    @Deprecated(since = "0.4.5")
    public UpdateStatement<T> setRaw(String expression) {
        // Basic validation for obvious injection patterns
        String lower = expression.toLowerCase();
        if (lower.contains("'; ") || lower.contains("'--") || lower.contains("' or ") || lower.contains("' and ")) {
            throw new SecurityException(
                "Potential SQL injection detected in setRaw(). " +
                "Do not use user input in raw expressions."
            );
        }
        this.fields.put(expression, null); // null = raw expression
        return this;
    }

    /**
     * Validates that a field name is safe (prevents SQL injection via field names).
     */
    private void validateFieldName(String field) {
        if (field == null || field.isEmpty()) {
            throw new IllegalArgumentException("Field name cannot be null or empty");
        }
        // Allow: letters, numbers, underscores, dots (for nested fields)
        if (!field.matches("^[a-zA-Z_][a-zA-Z0-9_.]*$")) {
            throw new SecurityException(
                "Invalid field name (potential SQL injection): " + field
            );
        }
    }

    /**
     * Merge partial data using MERGE clause.
     *
     * @param content the content to merge
     * @return this statement for chaining
     */
    public UpdateStatement<T> merge(String content) {
        this.mergeContent = content;
        return this;
    }

    /**
     * Replace entire record using CONTENT clause.
     *
     * @param content the new content
     * @return this statement for chaining
     */
    public UpdateStatement<T> content(String content) {
        this.content = content;
        return this;
    }

    // --- WHERE Clause (Parameterized - Default) ---

    /**
     * Adds a parameterized WHERE condition (SQL injection protected).
     *
     * <p>This is the recommended and default way to add WHERE conditions.
     *
     * <p>Example:
     * <pre>{@code
     * UpdateStatement.table(User.class)
     *     .set("active", true)
     *     .where("email", "=", userInput)
     *     .execute(client);
     * }</pre>
     *
     * @param field the field name
     * @param operator the comparison operator (=, !=, >, <, >=, <=, LIKE, IN)
     * @param value the value (will be parameterized)
     * @return this statement for chaining
     */
    public UpdateStatement<T> where(String field, String operator, Object value) {
        whereClause.addSafe(field, operator, value);
        return this;
    }

    /**
     * Adds a parameterized AND condition.
     *
     * @param field the field name
     * @param operator the comparison operator
     * @param value the value (will be parameterized)
     * @return this statement for chaining
     */
    public UpdateStatement<T> and(String field, String operator, Object value) {
        whereClause.andSafe(field, operator, value);
        return this;
    }

    /**
     * Adds a parameterized OR condition.
     *
     * @param field the field name
     * @param operator the comparison operator
     * @param value the value (will be parameterized)
     * @return this statement for chaining
     */
    public UpdateStatement<T> or(String field, String operator, Object value) {
        whereClause.orSafe(field, operator, value);
        return this;
    }

    // --- WHERE Clause (Raw - Deprecated but backward-compatible) ---

    /**
     * Adds a raw WHERE condition (for backward compatibility).
     *
     * <p><b>⚠️ DEPRECATED:</b> Use {@link #where(String, String, Object)} instead.
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #where(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public UpdateStatement<T> where(String condition) {
        whereClause.add(condition);
        return this;
    }

    /**
     * Adds a raw WHERE condition (alias).
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #where(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public UpdateStatement<T> whereRaw(String condition) {
        whereClause.add(condition);
        return this;
    }

    /**
     * Adds a raw AND condition (for backward compatibility).
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #and(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public UpdateStatement<T> and(String condition) {
        whereClause.and(condition);
        return this;
    }

    /**
     * Adds a raw AND condition (alias).
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #and(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public UpdateStatement<T> andRaw(String condition) {
        whereClause.and(condition);
        return this;
    }

    /**
     * Adds a raw OR condition (for backward compatibility).
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #or(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public UpdateStatement<T> or(String condition) {
        whereClause.or(condition);
        return this;
    }

    /**
     * Adds a raw OR condition (alias).
     *
     * @param condition the raw condition
     * @return this statement for chaining
     * @deprecated Use parameterized {@link #or(String, String, Object)} instead
     */
    @Deprecated(since = "0.4.5")
    public UpdateStatement<T> orRaw(String condition) {
        whereClause.or(condition);
        return this;
    }

    // --- Legacy Safe Methods (now just aliases) ---

    /**
     * @deprecated Use {@link #where(String, String, Object)} instead - it's now the default
     */
    @Deprecated(since = "0.4.5")
    public UpdateStatement<T> whereSafe(String field, String operator, Object value) {
        return where(field, operator, value);
    }

    /**
     * @deprecated Use {@link #and(String, String, Object)} instead - it's now the default
     */
    @Deprecated(since = "0.4.5")
    public UpdateStatement<T> andSafe(String field, String operator, Object value) {
        return and(field, operator, value);
    }

    /**
     * @deprecated Use {@link #or(String, String, Object)} instead - it's now the default
     */
    @Deprecated(since = "0.4.5")
    public UpdateStatement<T> orSafe(String field, String operator, Object value) {
        return or(field, operator, value);
    }

    /**
     * Use ONLY keyword to return a single object instead of array.
     *
     * @return this statement for chaining
     */
    public UpdateStatement<T> only() {
        this.onlyOne = true;
        return this;
    }

    /**
     * Return nothing (RETURN NONE).
     *
     * @return this statement for chaining
     */
    public UpdateStatement<T> returnNone() {
        this.returnClause = "NONE";
        return this;
    }

    /**
     * Return record before changes (RETURN BEFORE).
     *
     * @return this statement for chaining
     */
    public UpdateStatement<T> returnBefore() {
        this.returnClause = "BEFORE";
        return this;
    }

    /**
     * Return record after changes (RETURN AFTER) - default.
     *
     * @return this statement for chaining
     */
    public UpdateStatement<T> returnAfter() {
        this.returnClause = "AFTER";
        return this;
    }

    /**
     * Return changeset diff (RETURN DIFF).
     *
     * @return this statement for chaining
     */
    public UpdateStatement<T> returnDiff() {
        this.returnClause = "DIFF";
        return this;
    }

    /**
     * Set query timeout.
     *
     * @param duration the timeout duration (e.g. "5s", "100ms")
     * @return this statement for chaining
     */
    public UpdateStatement<T> timeout(String duration) {
        this.timeoutClause = duration;
        return this;
    }

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder("UPDATE");

        if (onlyOne) {
            sql.append(" ONLY");
        }

        sql.append(" ").append(target);

        // SET clause
        if (!fields.isEmpty()) {
            sql.append(" SET ");
            boolean first = true;
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                if (!first) sql.append(", ");
                if (entry.getValue() == null) {
                    // Raw expression
                    sql.append(entry.getKey());
                } else {
                    sql.append(entry.getKey()).append(" = ");
                    sql.append(formatValue(entry.getValue()));
                }
                first = false;
            }
        }

        // MERGE clause
        if (mergeContent != null) {
            sql.append(" MERGE ").append(mergeContent);
        }

        // CONTENT clause
        if (content != null) {
            sql.append(" CONTENT ").append(content);
        }

        // WHERE clause
        if (!whereClause.isEmpty()) {
            sql.append(whereClause.toSql());
        }

        // RETURN clause
        if (returnClause != null && !returnClause.equals("AFTER")) {
            sql.append(" RETURN ").append(returnClause);
        }

        // TIMEOUT clause
        if (timeoutClause != null) {
            sql.append(" TIMEOUT ").append(timeoutClause);
        }

        return sql.toString();
    }

    @Override
    public Flux<T> execute(OneirosClient client) {
        String sql = toSql();
        // Use parameterized query if WHERE has parameters
        if (whereClause.hasParameters()) {
            return client.query(sql, whereClause.getParameters(), type);
        }
        return client.query(sql, type);
    }

    @Override
    public Mono<T> executeOne(OneirosClient client) {
        String sql = toSql();
        // Use parameterized query if WHERE has parameters
        if (whereClause.hasParameters()) {
            return client.query(sql, whereClause.getParameters(), type).next();
        }
        return client.query(sql, type).next();
    }

    // --- Helpers ---

    private static String getTableName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(OneirosEntity.class)) {
            String val = clazz.getAnnotation(OneirosEntity.class).value();
            return val.isEmpty() ? clazz.getSimpleName().toLowerCase() : val;
        }
        return clazz.getSimpleName().toLowerCase();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NONE";
        } else if (value instanceof String) {
            return "'" + escapeString(value.toString()) + "'";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else {
            return "'" + escapeString(value.toString()) + "'";
        }
    }

    /**
     * Escapes special characters in a string to prevent SQL injection.
     */
    private String escapeString(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")  // Backslash first!
            .replace("'", "\\'")    // Single quotes
            .replace("\"", "\\\"")  // Double quotes
            .replace("\n", "\\n")   // Newlines
            .replace("\r", "\\r")   // Carriage returns
            .replace("\t", "\\t")   // Tabs
            .replace("\0", "");     // Remove null bytes
    }
}
