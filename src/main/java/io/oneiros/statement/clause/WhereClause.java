package io.oneiros.statement.clause;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WHERE clause for filtering results with SQL injection protection.
 *
 * <p><b>⚠️ SECURITY WARNING:</b> For user input, always use parameterized conditions:
 * <pre>{@code
 * // ✅ SAFE: Using parameters
 * .where("email = $email")
 * .param("email", userInput)
 *
 * // ❌ UNSAFE: String concatenation
 * .where("email = '" + userInput + "'")
 * }</pre>
 *
 * <p>Example:
 * <pre>{@code
 * WHERE name = 'Alice' AND age > 18
 * }</pre>
 */
public class WhereClause implements Clause {

    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> parameters = new HashMap<>();

    /**
     * Adds a condition to the WHERE clause.
     *
     * <p><b>⚠️ WARNING:</b> Do NOT concatenate user input directly into conditions.
     * Use {@link #addSafe(String, String, Object)} for user input.
     *
     * @param condition the condition expression (e.g., "age > 18")
     * @return this clause for chaining
     */
    public WhereClause add(String condition) {
        validateCondition(condition);
        conditions.add(condition);
        return this;
    }

    /**
     * Adds a safe parameterized condition (SQL injection protected).
     *
     * <p>Example:
     * <pre>{@code
     * whereClause.addSafe("email", "=", userInput);
     * // Generates: email = $email_0
     * }</pre>
     *
     * @param field the field name
     * @param operator the comparison operator (=, !=, >, <, >=, <=, LIKE, IN)
     * @param value the value (will be parameterized)
     * @return this clause for chaining
     */
    public WhereClause addSafe(String field, String operator, Object value) {
        validateFieldName(field);
        validateOperator(operator);

        String paramName = field.replace(".", "_") + "_" + parameters.size();
        parameters.put(paramName, value);
        conditions.add(field + " " + operator + " $" + paramName);
        return this;
    }

    /**
     * Adds an AND condition.
     *
     * @param condition the condition expression
     * @return this clause for chaining
     */
    public WhereClause and(String condition) {
        validateCondition(condition);
        conditions.add("AND " + condition);
        return this;
    }

    /**
     * Adds a safe AND condition with parameter binding.
     *
     * @param field the field name
     * @param operator the comparison operator
     * @param value the value (will be parameterized)
     * @return this clause for chaining
     */
    public WhereClause andSafe(String field, String operator, Object value) {
        validateFieldName(field);
        validateOperator(operator);

        String paramName = field.replace(".", "_") + "_" + parameters.size();
        parameters.put(paramName, value);
        conditions.add("AND " + field + " " + operator + " $" + paramName);
        return this;
    }

    /**
     * Adds an OR condition.
     *
     * @param condition the condition expression
     * @return this clause for chaining
     */
    public WhereClause or(String condition) {
        validateCondition(condition);
        conditions.add("OR " + condition);
        return this;
    }

    /**
     * Adds a safe OR condition with parameter binding.
     *
     * @param field the field name
     * @param operator the comparison operator
     * @param value the value (will be parameterized)
     * @return this clause for chaining
     */
    public WhereClause orSafe(String field, String operator, Object value) {
        validateFieldName(field);
        validateOperator(operator);

        String paramName = field.replace(".", "_") + "_" + parameters.size();
        parameters.put(paramName, value);
        conditions.add("OR " + field + " " + operator + " $" + paramName);
        return this;
    }

    /**
     * Returns the parameters map for parameterized execution.
     *
     * @return map of parameter names to values
     */
    public Map<String, Object> getParameters() {
        return new HashMap<>(parameters);
    }

    /**
     * Checks if the clause has parameters.
     *
     * @return true if parameters exist
     */
    public boolean hasParameters() {
        return !parameters.isEmpty();
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    @Override
    public String toSql() {
        if (conditions.isEmpty()) {
            return "";
        }

        StringBuilder sql = new StringBuilder(" WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            String condition = conditions.get(i);
            if (i > 0 && !condition.startsWith("AND ") && !condition.startsWith("OR ")) {
                sql.append(" AND ");
            }
            sql.append(condition);
        }
        return sql.toString();
    }

    // ==================== Security Validation ====================

    private static final String[] ALLOWED_OPERATORS = {
        "=", "!=", "<>", ">", "<", ">=", "<=",
        "LIKE", "NOT LIKE", "IN", "NOT IN",
        "CONTAINS", "CONTAINSALL", "CONTAINSANY", "CONTAINSNONE",
        "INSIDE", "OUTSIDE", "INTERSECTS"
    };

    /**
     * Validates that a field name is safe (prevents SQL injection via field names).
     */
    private void validateFieldName(String field) {
        if (field == null || field.isEmpty()) {
            throw new IllegalArgumentException("Field name cannot be null or empty");
        }
        // Allow: letters, numbers, underscores, dots (for nested fields), colons (for record IDs)
        if (!field.matches("^[a-zA-Z_][a-zA-Z0-9_.:\\[\\]]*$")) {
            throw new SecurityException(
                "Invalid field name (potential SQL injection): " + field +
                ". Allowed: letters, numbers, underscores, dots, colons."
            );
        }
    }

    /**
     * Validates that an operator is in the allowed list.
     */
    private void validateOperator(String operator) {
        if (operator == null || operator.isEmpty()) {
            throw new IllegalArgumentException("Operator cannot be null or empty");
        }
        String upperOp = operator.toUpperCase().trim();
        for (String allowed : ALLOWED_OPERATORS) {
            if (allowed.equals(upperOp)) {
                return;
            }
        }
        throw new SecurityException(
            "Invalid operator (potential SQL injection): " + operator +
            ". Allowed: " + String.join(", ", ALLOWED_OPERATORS)
        );
    }

    /**
     * Validates a raw condition for obvious SQL injection patterns.
     * Note: This is a basic check - parameterized queries are always preferred.
     */
    private void validateCondition(String condition) {
        if (condition == null || condition.isEmpty()) {
            throw new IllegalArgumentException("Condition cannot be null or empty");
        }

        String lower = condition.toLowerCase();

        // Check for dangerous patterns
        String[] dangerousPatterns = {
            "'; --", "'; drop", "'; delete", "'; update", "'; insert",
            "' or '1'='1", "' or 1=1", "'; exec", "'; execute",
            "union select", "union all select"
        };

        for (String pattern : dangerousPatterns) {
            if (lower.contains(pattern)) {
                throw new SecurityException(
                    "Potential SQL injection detected in condition: " + condition +
                    ". Use parameterized queries with addSafe() instead."
                );
            }
        }
    }
}
