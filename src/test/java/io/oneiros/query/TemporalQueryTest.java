package io.oneiros.query;

import io.oneiros.statement.statements.SelectStatement;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalQueryTest {

    @Test
    void testOneirosQueryTemporalSql() {
        Instant now = Instant.parse("2026-04-17T20:00:00Z");
        
        String sql = OneirosQuery.select(Object.class)
                .at(now)
                .where("id").is("record:1")
                .toSql();
        
        assertTrue(sql.contains("VERSION d'2026-04-17T20:00:00Z'"), "SQL should contain VERSION clause");
        assertTrue(sql.indexOf("FROM") < sql.indexOf("VERSION"), "VERSION should be after FROM");
        assertTrue(sql.indexOf("VERSION") < sql.indexOf("WHERE"), "VERSION should be before WHERE");
    }

    @Test
    void testSelectStatementTemporalSql() {
        Instant now = Instant.parse("2026-04-17T20:00:00Z");
        
        String sql = SelectStatement.from(Object.class)
                .at(now)
                .where("id", "=", "record:1")
                .toSql();
        
        assertTrue(sql.contains("VERSION d'2026-04-17T20:00:00Z'"), "SQL should contain VERSION clause");
        assertTrue(sql.indexOf("FROM") < sql.indexOf("VERSION"), "VERSION should be after FROM");
        assertTrue(sql.indexOf("VERSION") < sql.indexOf("WHERE"), "VERSION should be before WHERE");
    }
}
