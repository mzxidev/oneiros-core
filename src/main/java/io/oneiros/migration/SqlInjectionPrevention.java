package io.oneiros.migration;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * SQL Injection Prevention utilities for SurrealDB queries.
 *
 * <p>This class provides safe query building with parameter binding
 * and input validation to prevent SQL injection attacks.
 *
 * @since 0.4.3
 */
public class SqlInjectionPrevention {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * Validates that a table name is safe (alphanumeric + underscore).
     *
     * @param tableName the table name to validate
     * @throws IllegalArgumentException if table name is invalid
     */
    public static void validateTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        if (!TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid table name: " + tableName + " (only alphanumeric and underscore allowed)");
        }
    }

    /**
     * Validates that an identifier is safe (field name, variable name, etc.).
     *
     * @param identifier the identifier to validate
     * @throws IllegalArgumentException if identifier is invalid
     */
    public static void validateIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("Identifier cannot be null or empty");
        }
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    "Invalid identifier: " + identifier + " (only alphanumeric and underscore allowed)");
        }
    }

    /**
     * Escapes a string value for use in SurrealQL queries.
     *
     * <p><strong>WARNING:</strong> Prefer parameter binding over manual escaping!
     * Use this only when parameter binding is not possible.
     *
     * @param value the value to escape
     * @return escaped value safe for embedding in queries
     */
    public static String escapeStringValue(String value) {
        if (value == null) {
            return "NULL";
        }
        // Escape single quotes, backslashes, and control characters
        return value
                .replace("\\", "\\\\")  // Backslash must be first
                .replace("'", "\\'")     // Single quote
                .replace("\"", "\\\"")   // Double quote
                .replace("\n", "\\n")    // Newline
                .replace("\r", "\\r")    // Carriage return
                .replace("\t", "\\t")    // Tab
                .replace("\0", "")       // Null byte (remove)
                ;
    }

    /**
     * Validates that a record ID matches the expected format: table:id
     * Prevents SQL injection via record ID fields in RELATE statements.
     *
     * <p>Valid formats:
     * <ul>
     *   <li>user:alice</li>
     *   <li>user:123</li>
     *   <li>user:⟨uuid⟩</li>
     * </ul>
     *
     * @param recordId the record ID to validate
     * @throws IllegalArgumentException if record ID format is invalid
     */
    public static void validateRecordId(String recordId) {
        if (recordId == null || recordId.isEmpty()) {
            throw new IllegalArgumentException("Record ID cannot be null or empty");
        }
        // Must contain exactly one colon separating table:id
        if (!recordId.matches("^[a-zA-Z_][a-zA-Z0-9_]*:[a-zA-Z0-9_\\-]+$")) {
            throw new IllegalArgumentException(
                    "Invalid record ID format: " + recordId + " (expected table:id with safe characters)");
        }
    }

    /**
     * Creates a safe parameterized query for migration history recording.
     *
     * @param tableName the history table name (validated)
     * @return parameterized query string
     */
    public static String createMigrationHistoryQuery(String tableName) {
        validateTableName(tableName);
        return "CREATE " + tableName + " SET " +
               "version = $version, " +
               "description = $description, " +
               "installed_on = time::now(), " +
               "execution_time_ms = $execution_time_ms, " +
               "success = $success, " +
               "error_message = $error_message";
    }

    /**
     * Creates a safe parameterized query for fetching migration history.
     *
     * @param tableName the history table name (validated)
     * @return parameterized query string
     */
    public static String createFetchHistoryQuery(String tableName) {
        validateTableName(tableName);
        return "SELECT * FROM " + tableName + " WHERE success = true ORDER BY version DESC LIMIT 1";
    }

    /**
     * Validates SQL-like patterns that could indicate injection attempts.
     *
     * @param input the input to validate
     * @return true if input looks suspicious
     */
    public static boolean isSuspiciousInput(String input) {
        if (input == null) {
            return false;
        }

        String lower = input.toLowerCase();

        // Check for common SQL injection patterns
        return lower.contains("--") ||          // SQL comments
               lower.contains("/*") ||          // Multi-line comments
               lower.contains("*/") ||
               lower.contains(";") ||           // Statement separator
               lower.contains("union") ||       // UNION attacks
               lower.contains("drop ") ||       // Destructive commands
               lower.contains("delete ") ||
               lower.contains("truncate ") ||
               lower.contains("update ") ||
               lower.contains("insert ") ||
               lower.contains("exec") ||        // Command execution
               lower.contains("xp_") ||         // SQL Server extended procs
               lower.contains("<script") ||     // XSS
               lower.contains("javascript:") ||
               lower.contains("onerror=") ||
               lower.contains("onload=");
    }

    /**
     * Sanitizes input by removing potentially dangerous characters.
     *
     * <p><strong>WARNING:</strong> Sanitization is NOT a substitute for parameter binding!
     * Use parameter binding whenever possible.
     *
     * @param input the input to sanitize
     * @return sanitized input
     */
    public static String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }

        // Remove control characters and dangerous patterns
        return input
                .replaceAll("[\\x00-\\x1F\\x7F]", "")  // Control characters
                .replaceAll("--", "")                   // SQL comments
                .replaceAll("/\\*", "")                 // Block comment start
                .replaceAll("\\*/", "")                 // Block comment end
                .trim();
    }

    /**
     * Creates safe parameters for migration history recording.
     *
     * @param entry the schema history entry
     * @return parameter map for query binding
     */
    public static Map<String, Object> createMigrationHistoryParams(SchemaHistoryEntry entry) {
        return Map.of(
                "version", entry.getVersion(),
                "description", entry.getDescription(),  // No escaping needed - parameter binding handles it
                "execution_time_ms", entry.getExecutionTimeMs(),
                "success", entry.isSuccess(),
                "error_message", entry.getErrorMessage() != null ? entry.getErrorMessage() : ""
        );
    }
}
