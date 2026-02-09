package io.oneiros.client;

import java.util.regex.Pattern;

/**
 * Input validation for RPC method parameters to prevent NoSQL injection and malicious inputs.
 *
 * <p>Validates table names, record IDs, and other user-supplied parameters before
 * sending them to SurrealDB.
 *
 * @since 0.4.3
 */
public class InputValidator {

    // SurrealDB table name pattern: alphanumeric + underscore, must start with letter/underscore
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    // Record ID pattern: table:id format
    private static final Pattern RECORD_ID_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}:[a-zA-Z0-9_-]+$");

    // Simple identifier pattern
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,255}$");

    /**
     * Validates a table name.
     *
     * @param tableName the table name to validate
     * @throws IllegalArgumentException if invalid
     */
    public static void validateTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }

        if (tableName.length() > 64) {
            throw new IllegalArgumentException("Table name too long (max 64 characters): " + tableName);
        }

        if (!TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException(
                "Invalid table name: '" + tableName + "'. " +
                "Must start with letter/underscore and contain only alphanumeric characters and underscores."
            );
        }

        // Check for SQL injection patterns
        if (containsSqlInjectionPattern(tableName)) {
            throw new IllegalArgumentException("Suspicious characters in table name: " + tableName);
        }
    }

    /**
     * Validates a record ID (table:id format).
     *
     * @param recordId the record ID to validate
     * @throws IllegalArgumentException if invalid
     */
    public static void validateRecordId(String recordId) {
        if (recordId == null || recordId.isEmpty()) {
            throw new IllegalArgumentException("Record ID cannot be null or empty");
        }

        if (!RECORD_ID_PATTERN.matcher(recordId).matches()) {
            throw new IllegalArgumentException(
                "Invalid record ID format: '" + recordId + "'. " +
                "Expected format: table:id (e.g., 'users:john')"
            );
        }

        if (containsSqlInjectionPattern(recordId)) {
            throw new IllegalArgumentException("Suspicious characters in record ID: " + recordId);
        }
    }

    /**
     * Validates a generic identifier (field name, variable name, etc.).
     *
     * @param identifier the identifier to validate
     * @throws IllegalArgumentException if invalid
     */
    public static void validateIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("Identifier cannot be null or empty");
        }

        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                "Invalid identifier: '" + identifier + "'. " +
                "Must start with letter/underscore and contain only alphanumeric characters and underscores."
            );
        }
    }

    /**
     * Validates data object for suspicious content.
     *
     * @param data the data to validate
     * @throws IllegalArgumentException if data is suspicious
     */
    public static void validateData(Object data) {
        if (data == null) {
            return; // null is allowed
        }

        // For strings, check for injection patterns
        if (data instanceof String) {
            String str = (String) data;
            if (containsSqlInjectionPattern(str)) {
                throw new IllegalArgumentException(
                    "Data contains suspicious SQL injection patterns"
                );
            }

            // Check for excessive length (DoS protection)
            if (str.length() > 1_000_000) { // 1MB limit
                throw new IllegalArgumentException(
                    "Data string too large (max 1MB): " + str.length() + " characters"
                );
            }
        }

        // For collections, validate size (DoS protection)
        if (data instanceof java.util.Collection) {
            java.util.Collection<?> collection = (java.util.Collection<?>) data;
            if (collection.size() > 10_000) {
                throw new IllegalArgumentException(
                    "Collection too large (max 10,000 items): " + collection.size()
                );
            }
        }

        if (data instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) data;
            if (map.size() > 1_000) {
                throw new IllegalArgumentException(
                    "Map too large (max 1,000 entries): " + map.size()
                );
            }
        }
    }

    /**
     * Checks if input contains SQL/NoSQL injection patterns.
     *
     * @param input the input to check
     * @return true if suspicious patterns detected
     */
    private static boolean containsSqlInjectionPattern(String input) {
        if (input == null) {
            return false;
        }

        String lower = input.toLowerCase();

        // SQL injection patterns
        return lower.contains("--") ||          // SQL comment
               lower.contains("/*") ||          // Block comment
               lower.contains("*/") ||
               lower.contains(";drop ") ||      // Destructive
               lower.contains(";delete ") ||
               lower.contains(";truncate ") ||
               lower.contains("' or '1'='1") || // Classic injection
               lower.contains("\" or \"1\"=\"1") ||
               lower.contains("' or 1=1") ||
               lower.contains("admin'--") ||
               lower.contains("||") ||          // String concatenation attacks
               lower.contains("&&") ||
               lower.contains("exec(") ||       // Code execution
               lower.contains("execute(") ||
               lower.contains("script>") ||     // XSS
               lower.contains("javascript:") ||
               lower.contains("onerror=");
    }

    /**
     * Sanitizes a string by removing control characters.
     *
     * <p><strong>WARNING:</strong> Prefer validation over sanitization!
     *
     * @param input the input to sanitize
     * @return sanitized string
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        return input
            .replaceAll("[\\x00-\\x1F\\x7F]", "") // Remove control chars
            .trim();
    }

    /**
     * Validates that a string is within size limits.
     *
     * @param input the input to check
     * @param maxLength maximum allowed length
     * @throws IllegalArgumentException if too long
     */
    public static void validateLength(String input, int maxLength) {
        if (input != null && input.length() > maxLength) {
            throw new IllegalArgumentException(
                "Input too long: " + input.length() + " characters (max: " + maxLength + ")"
            );
        }
    }

    /**
     * Validates SQL query string for safe execution.
     *
     * @param sql the SQL query to validate
     * @throws IllegalArgumentException if suspicious
     */
    public static void validateSqlQuery(String sql) {
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("SQL query cannot be null or empty");
        }

        if (sql.length() > 100_000) {
            throw new IllegalArgumentException(
                "SQL query too large (max 100KB): " + sql.length() + " characters"
            );
        }

        // Allow only SELECT, INSERT, UPDATE, DELETE, CREATE, DEFINE (safe operations)
        String trimmed = sql.trim().toLowerCase();
        boolean isAllowed = trimmed.startsWith("select ") ||
                           trimmed.startsWith("insert ") ||
                           trimmed.startsWith("update ") ||
                           trimmed.startsWith("delete ") ||
                           trimmed.startsWith("create ") ||
                           trimmed.startsWith("define ") ||
                           trimmed.startsWith("relate ") ||
                           trimmed.startsWith("let ") ||
                           trimmed.startsWith("begin ") ||
                           trimmed.startsWith("commit ") ||
                           trimmed.startsWith("cancel ");

        if (!isAllowed) {
            throw new IllegalArgumentException(
                "Unsupported SQL operation. Query must start with SELECT, INSERT, UPDATE, DELETE, CREATE, DEFINE, RELATE, LET, BEGIN, COMMIT, or CANCEL."
            );
        }
    }
}
