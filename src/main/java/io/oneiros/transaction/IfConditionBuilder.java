package io.oneiros.transaction;

/**
 * Conditional statement builder for IF/ELSE logic in transactions.
 *
 * Example:
 * <pre>
 * TransactionBuilder.begin()
 *     .ifCondition()
 *         .field("balance").lt(100)
 *         .then()
 *             .throwError("Insufficient funds!")
 *         .endIf()
 *     .commit(client);
 * </pre>
 */
public class IfConditionBuilder {

    private final TransactionBuilder parent;
    private final StringBuilder condition = new StringBuilder();
    private final StringBuilder thenBlock = new StringBuilder();
    private String elseBlock;

    public IfConditionBuilder(TransactionBuilder parent) {
        this.parent = parent;
    }

    // --- Condition Building ---

    public IfConditionBuilder field(String fieldName) {
        // Füge nur ein Leerzeichen hinzu, wenn die Condition nicht leer ist
        // UND nicht mit "!" endet (für NOT-Operator)
        if (!condition.isEmpty() && !condition.toString().endsWith("!")) {
            condition.append(" ");
        }
        condition.append(fieldName);
        return this;
    }

    public IfConditionBuilder eq(Object value) {
        condition.append(" = ").append(formatValue(value));
        return this;
    }

    public IfConditionBuilder notEquals(Object value) {
        condition.append(" != ").append(formatValue(value));
        return this;
    }

    public IfConditionBuilder gt(Object value) {
        condition.append(" > ").append(formatValue(value));
        return this;
    }

    public IfConditionBuilder lt(Object value) {
        condition.append(" < ").append(formatValue(value));
        return this;
    }

    public IfConditionBuilder gte(Object value) {
        condition.append(" >= ").append(formatValue(value));
        return this;
    }

    public IfConditionBuilder lte(Object value) {
        condition.append(" <= ").append(formatValue(value));
        return this;
    }

    public IfConditionBuilder isTrue() {
        condition.append(" = true");
        return this;
    }

    public IfConditionBuilder isFalse() {
        condition.append(" = false");
        return this;
    }

    public IfConditionBuilder isNull() {
        condition.append(" IS NULL");
        return this;
    }

    public IfConditionBuilder isNotNull() {
        condition.append(" IS NOT NULL");
        return this;
    }

    public IfConditionBuilder and() {
        condition.append(" AND");
        return this;
    }

    public IfConditionBuilder or() {
        condition.append(" OR");
        return this;
    }

    public IfConditionBuilder not() {
        condition.append("!");
        return this;
    }

    // --- Then Block ---

    public IfConditionBuilder then() {
        return this;
    }

    public IfConditionBuilder throwError(String message) {
        thenBlock.append("THROW \"").append(message).append("\";");
        return this;
    }

    public IfConditionBuilder addStatement(String statement) {
        thenBlock.append(statement);
        if (!statement.trim().endsWith(";")) {
            thenBlock.append(";");
        }
        return this;
    }

    // --- Nested Statement Builders (for THEN block) ---

    public UpdateInIfBuilder update(String target) {
        return new UpdateInIfBuilder(this, target);
    }

    public CreateInIfBuilder create(String target) {
        return new CreateInIfBuilder(this, target);
    }

    // --- Proxy classes to capture statements and return to IF builder ---
    // Diese Klassen umgehen die Proxy-Implementierung und verwenden stattdessen direkte Statement-Erfassung

    public static class UpdateInIfBuilder {
        private final IfConditionBuilder ifBuilder;
        private final String target;
        private final StringBuilder setClause = new StringBuilder();
        private String whereClause;

        UpdateInIfBuilder(IfConditionBuilder ifBuilder, String target) {
            this.ifBuilder = ifBuilder;
            this.target = target;
        }

        public UpdateInIfBuilder set(String field, Object value) {
            if (!setClause.isEmpty()) {
                setClause.append(", ");
            }
            setClause.append(field).append(" = ");
            if (value instanceof Number || value instanceof Boolean) {
                setClause.append(value);
            } else {
                setClause.append("'").append(value.toString().replace("'", "\\'")).append("'");
            }
            return this;
        }

        public UpdateInIfBuilder set(String expression) {
            if (!setClause.isEmpty()) {
                setClause.append(", ");
            }
            setClause.append(expression);
            return this;
        }

        public UpdateInIfBuilder where(String condition) {
            this.whereClause = condition;
            return this;
        }

        public IfConditionBuilder endUpdate() {
            StringBuilder sql = new StringBuilder("UPDATE ");
            sql.append(target);
            if (!setClause.isEmpty()) {
                sql.append(" SET ").append(setClause);
            }
            if (whereClause != null) {
                sql.append(" WHERE ").append(whereClause);
            }
            ifBuilder.thenBlock.append(sql).append(";");
            return ifBuilder;
        }

        public TransactionBuilder endIf() {
            endUpdate();
            return ifBuilder.endIf();
        }
    }

    public static class CreateInIfBuilder {
        private final IfConditionBuilder ifBuilder;
        private final String target;
        private final StringBuilder setClause = new StringBuilder();

        CreateInIfBuilder(IfConditionBuilder ifBuilder, String target) {
            this.ifBuilder = ifBuilder;
            this.target = target;
        }

        public CreateInIfBuilder set(String field, Object value) {
            if (!setClause.isEmpty()) {
                setClause.append(", ");
            }
            setClause.append(field).append(" = ");
            if (value instanceof Number || value instanceof Boolean) {
                setClause.append(value);
            } else {
                setClause.append("'").append(value.toString().replace("'", "\\'")).append("'");
            }
            return this;
        }

        public CreateInIfBuilder set(String expression) {
            if (!setClause.isEmpty()) {
                setClause.append(", ");
            }
            setClause.append(expression);
            return this;
        }

        public IfConditionBuilder endCreate() {
            StringBuilder sql = new StringBuilder("CREATE ");
            sql.append(target);
            if (!setClause.isEmpty()) {
                sql.append(" SET ").append(setClause);
            }
            ifBuilder.thenBlock.append(sql).append(";");
            return ifBuilder;
        }

        public TransactionBuilder endIf() {
            endCreate();
            return ifBuilder.endIf();
        }
    }

    // --- Else Block ---

    public IfConditionBuilder elseBlock() {
        return this;
    }

    public IfConditionBuilder elseThrowError(String message) {
        elseBlock = "THROW \"" + message + "\";";
        return this;
    }

    public IfConditionBuilder elseStatement(String statement) {
        elseBlock = statement;
        if (!statement.trim().endsWith(";")) {
            elseBlock += ";";
        }
        return this;
    }

    // --- End ---

    public TransactionBuilder endIf() {
        StringBuilder sql = new StringBuilder("IF ");
        sql.append(condition);
        sql.append(" { ");
        sql.append(thenBlock);
        sql.append(" }");

        if (elseBlock != null) {
            sql.append(" ELSE { ");
            sql.append(elseBlock);
            sql.append(" }");
        }

        sql.append(";");

        parent.addRawStatement(sql.toString());
        return parent;
    }

    private String formatValue(Object val) {
        if (val instanceof Number) return val.toString();
        if (val instanceof Boolean) return val.toString();
        return "'" + val.toString().replace("'", "\\'") + "'";
    }
}
