package io.oneiros.statement;

import io.oneiros.statement.clause.WhereClause;
import io.oneiros.statement.statements.SelectStatement;
import io.oneiros.statement.statements.UpdateStatement;
import io.oneiros.statement.statements.DeleteStatement;
import io.oneiros.statement.statements.CreateStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for SQL injection protection in Statement API.
 */
@SuppressWarnings("deprecation")
class SqlInjectionProtectionTest {

    // ==================== WhereClause Tests ====================

    @Test
    @DisplayName("WhereClause should detect SQL injection patterns in raw conditions")
    void whereClauseShouldDetectInjectionPatterns() {
        WhereClause clause = new WhereClause();

        // Test various injection patterns (using raw add which validates)
        assertThatThrownBy(() -> clause.add("email = ''; DROP TABLE users; --"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("SQL injection");

        assertThatThrownBy(() -> clause.add("name = '' OR '1'='1'"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("SQL injection");

        assertThatThrownBy(() -> clause.add("id = 1 UNION SELECT * FROM passwords"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("SQL injection");
    }

    @Test
    @DisplayName("WhereClause should allow safe conditions")
    void whereClauseShouldAllowSafeConditions() {
        WhereClause clause = new WhereClause();

        // These should work without exception
        assertThatCode(() -> clause.add("age > 18")).doesNotThrowAnyException();
        assertThatCode(() -> clause.and("status = 'active'")).doesNotThrowAnyException();
        assertThatCode(() -> clause.or("role = 'admin'")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("WhereClause addSafe should validate field names")
    void whereClauseAddSafeShouldValidateFieldNames() {
        WhereClause clause = new WhereClause();

        // Valid field names
        assertThatCode(() -> clause.addSafe("email", "=", "test@test.com"))
            .doesNotThrowAnyException();
        assertThatCode(() -> clause.addSafe("user_name", "=", "john"))
            .doesNotThrowAnyException();
        assertThatCode(() -> clause.addSafe("profile.address.city", "=", "Berlin"))
            .doesNotThrowAnyException();

        // Invalid field names (potential injection)
        assertThatThrownBy(() -> clause.addSafe("'; DROP TABLE", "=", "value"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Invalid field name");

        assertThatThrownBy(() -> clause.addSafe("field; --", "=", "value"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("WhereClause addSafe should validate operators")
    void whereClauseAddSafeShouldValidateOperators() {
        WhereClause clause = new WhereClause();

        // Valid operators
        assertThatCode(() -> clause.addSafe("age", "=", 18)).doesNotThrowAnyException();
        assertThatCode(() -> clause.addSafe("age", ">=", 18)).doesNotThrowAnyException();
        assertThatCode(() -> clause.addSafe("name", "LIKE", "%john%")).doesNotThrowAnyException();
        assertThatCode(() -> clause.addSafe("status", "IN", "active")).doesNotThrowAnyException();

        // Invalid operators
        assertThatThrownBy(() -> clause.addSafe("field", "DROP", "value"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Invalid operator");

        assertThatThrownBy(() -> clause.addSafe("field", "; DELETE FROM", "value"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("WhereClause should generate parameterized SQL")
    void whereClauseShouldGenerateParameterizedSql() {
        WhereClause clause = new WhereClause();
        clause.addSafe("email", "=", "test@test.com");
        clause.andSafe("status", "=", "active");

        String sql = clause.toSql();

        // Should contain parameter placeholders
        assertThat(sql).contains("$email_0");
        assertThat(sql).contains("$status_1");

        // Should have parameters
        assertThat(clause.hasParameters()).isTrue();
        assertThat(clause.getParameters()).containsEntry("email_0", "test@test.com");
        assertThat(clause.getParameters()).containsEntry("status_1", "active");
    }

    // ==================== CreateStatement Tests ====================

    @Test
    @DisplayName("CreateStatement should validate field names")
    void createStatementShouldValidateFieldNames() {
        assertThatThrownBy(() ->
            CreateStatement.table(TestEntity.class)
                .set("'; DROP TABLE", "value")
        ).isInstanceOf(SecurityException.class)
         .hasMessageContaining("Invalid field name");

        // Valid field name should work
        assertThatCode(() ->
            CreateStatement.table(TestEntity.class)
                .set("valid_field", "value")
        ).doesNotThrowAnyException();
    }

    // ==================== UpdateStatement Tests ====================

    @Test
    @DisplayName("UpdateStatement should validate field names")
    void updateStatementShouldValidateFieldNames() {
        assertThatThrownBy(() ->
            UpdateStatement.table(TestEntity.class)
                .set("field; DELETE", "value")
        ).isInstanceOf(SecurityException.class)
         .hasMessageContaining("Invalid field name");
    }

    @Test
    @DisplayName("UpdateStatement setRaw should detect injection")
    void updateStatementSetRawShouldDetectInjection() {
        assertThatThrownBy(() ->
            UpdateStatement.table(TestEntity.class)
                .setRaw("balance = 0'; DROP TABLE users; --")
        ).isInstanceOf(SecurityException.class)
         .hasMessageContaining("SQL injection");
    }

    @Test
    @DisplayName("UpdateStatement setRaw should allow safe expressions")
    void updateStatementSetRawShouldAllowSafeExpressions() {
        // Safe raw expressions should work
        assertThatCode(() ->
            UpdateStatement.table(TestEntity.class)
                .setRaw("balance += 100")
        ).doesNotThrowAnyException();

        assertThatCode(() ->
            UpdateStatement.table(TestEntity.class)
                .setRaw("login_count = login_count + 1")
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UpdateStatement where() should now be parameterized by default")
    void updateStatementWhereShouldBeParameterizedByDefault() {
        var statement = UpdateStatement.table(TestEntity.class)
            .set("active", true)
            .where("email", "=", "user@example.com");

        String sql = statement.toSql();
        assertThat(sql).contains("UPDATE");
        assertThat(sql).contains("$email_0");
    }

    // ==================== SelectStatement Tests ====================

    @Test
    @DisplayName("SelectStatement where() should be parameterized by default")
    void selectStatementWhereShouldBeParameterizedByDefault() {
        var statement = SelectStatement.from(TestEntity.class)
            .where("email", "=", "test@test.com")
            .and("age", ">=", 18);

        String sql = statement.toSql();
        assertThat(sql).contains("SELECT");
        assertThat(sql).contains("$email_0");
        assertThat(sql).contains("$age_1");
    }

    @Test
    @DisplayName("SelectStatement whereRaw should still work but is deprecated")
    void selectStatementWhereRawShouldStillWork() {
        var statement = SelectStatement.from(TestEntity.class)
            .whereRaw("status = 'active'");

        String sql = statement.toSql();
        assertThat(sql).contains("status = 'active'");
    }

    // ==================== DeleteStatement Tests ====================

    @Test
    @DisplayName("DeleteStatement where() should be parameterized by default")
    void deleteStatementWhereShouldBeParameterizedByDefault() {
        var statement = DeleteStatement.from(TestEntity.class)
            .where("id", "=", "user:123");

        String sql = statement.toSql();
        assertThat(sql).contains("DELETE");
        assertThat(sql).contains("$id_0");
    }

    // ==================== Test Entity ====================

    @io.oneiros.annotation.OneirosEntity("test_entities")
    static class TestEntity {
        private String id;
        private String name;
        private int age;
    }
}

