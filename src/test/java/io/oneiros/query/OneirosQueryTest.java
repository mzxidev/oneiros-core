package io.oneiros.query;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit Tests für OneirosQuery (ohne Spring, ohne Docker)
 */
@DisplayName("🔍 OneirosQuery Unit Tests")
class OneirosQueryTest {

    @Mock
    private OneirosClient mockClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("✅ Simple WHERE clause - SQL generation")
    void testSimpleWhereClause() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("name").is("Alice");

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity WHERE name = 'Alice'");
    }

    @Test
    @DisplayName("✅ Multiple WHERE with AND - SQL generation")
    void testMultipleWhereWithAnd() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("name").is("Alice")
                .and("age").gt(18);

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity WHERE name = 'Alice' AND age > 18");
    }

    @Test
    @DisplayName("✅ OR operator - SQL generation")
    void testOrOperator() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("role").is("ADMIN")
                .or("role").is("MODERATOR");

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity WHERE role = 'ADMIN' OR role = 'MODERATOR'");
    }

    @Test
    @DisplayName("✅ IN operator - SQL generation")
    void testInOperator() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("status").in("ACTIVE", "PENDING", "APPROVED");

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity WHERE status IN ['ACTIVE', 'PENDING', 'APPROVED']");
    }

    @Test
    @DisplayName("✅ LIKE operator - SQL generation")
    void testLikeOperator() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("email").like("@gmail.com");

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity WHERE email CONTAINS '@gmail.com'");
    }

    @Test
    @DisplayName("✅ NOT EQUALS operator - SQL generation")
    void testNotEqualsOperator() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("status").notEquals("DELETED");

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity WHERE status != 'DELETED'");
    }

    @Test
    @DisplayName("✅ Greater Than / Less Than - SQL generation")
    void testComparisonOperators() {
        // Greater Than
        var query1 = OneirosQuery.select(TestEntity.class)
                .where("age").gt(18);
        assertThat(query1.toSql()).isEqualTo("SELECT * FROM test_entity WHERE age > 18");

        // Less Than
        var query2 = OneirosQuery.select(TestEntity.class)
                .where("age").lt(65);
        assertThat(query2.toSql()).isEqualTo("SELECT * FROM test_entity WHERE age < 65");

        // Greater Than or Equal
        var query3 = OneirosQuery.select(TestEntity.class)
                .where("age").gte(18);
        assertThat(query3.toSql()).isEqualTo("SELECT * FROM test_entity WHERE age >= 18");

        // Less Than or Equal
        var query4 = OneirosQuery.select(TestEntity.class)
                .where("age").lte(65);
        assertThat(query4.toSql()).isEqualTo("SELECT * FROM test_entity WHERE age <= 65");
    }

    @Test
    @DisplayName("✅ BETWEEN operator - SQL generation")
    void testBetweenOperator() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("age").between(18, 65);

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity WHERE age >= 18 AND age <= 65");
    }

    @Test
    @DisplayName("✅ IS NULL / IS NOT NULL - SQL generation")
    void testNullChecks() {
        // IS NULL
        var query1 = OneirosQuery.select(TestEntity.class)
                .where("deletedAt").isNull();
        assertThat(query1.toSql()).isEqualTo("SELECT * FROM test_entity WHERE deletedAt IS NULL");

        // IS NOT NULL
        var query2 = OneirosQuery.select(TestEntity.class)
                .where("email").isNotNull();
        assertThat(query2.toSql()).isEqualTo("SELECT * FROM test_entity WHERE email IS NOT NULL");
    }

    @Test
    @DisplayName("✅ ORDER BY ASC - SQL generation")
    void testOrderByAsc() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .orderBy("name");

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity ORDER BY name ASC");
    }

    @Test
    @DisplayName("✅ ORDER BY DESC - SQL generation")
    void testOrderByDesc() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .orderByDesc("createdAt");

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity ORDER BY createdAt DESC");
    }

    @Test
    @DisplayName("✅ LIMIT - SQL generation")
    void testLimit() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .limit(10);

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity LIMIT 10");
    }

    @Test
    @DisplayName("✅ OFFSET (START) - SQL generation")
    void testOffset() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .offset(20)
                .limit(10);

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity START 20 LIMIT 10");
    }

    @Test
    @DisplayName("✅ Complex Query - WHERE + OR + ORDER + LIMIT")
    void testComplexQuery() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("role").in("ADMIN", "MODERATOR")
                .and("status").notEquals("DELETED")
                .and("email").isNotNull()
                .or("premium").is(true)
                .orderByDesc("createdAt")
                .limit(20);

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).contains("SELECT * FROM test_entity WHERE");
        assertThat(sql).contains("role IN ['ADMIN', 'MODERATOR']");
        assertThat(sql).contains("status != 'DELETED'");
        assertThat(sql).contains("email IS NOT NULL");
        assertThat(sql).contains("OR premium = true");
        assertThat(sql).contains("ORDER BY createdAt DESC");
        assertThat(sql).contains("LIMIT 20");
    }

    @Test
    @DisplayName("✅ fetch() calls client with correct SQL")
    void testFetchCallsClient() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("name").is("Alice");

        when(mockClient.query(anyString(), eq(TestEntity.class)))
                .thenReturn(Flux.just(new TestEntity("1", "Alice", 25)));

        // When
        Flux<TestEntity> result = query.fetch(mockClient);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(entity -> "Alice".equals(entity.name))
                .verifyComplete();
    }

    @Test
    @DisplayName("✅ fetchOne() returns single element")
    void testFetchOne() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("name").is("Bob");

        when(mockClient.query(anyString(), eq(TestEntity.class)))
                .thenReturn(Flux.just(new TestEntity("2", "Bob", 30)));

        // When
        var result = query.fetchOne(mockClient);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(entity -> "Bob".equals(entity.name))
                .verifyComplete();
    }

    @Test
    @DisplayName("✅ String escaping in values")
    void testStringEscaping() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("name").is("O'Brien");

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity WHERE name = 'O\\'Brien'");
    }

    @Test
    @DisplayName("✅ Number values without quotes")
    void testNumberValues() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("age").is(25)
                .and("score").gt(100.5);

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).contains("age = 25");
        assertThat(sql).contains("score > 100.5");
        assertThat(sql).doesNotContain("'25'");
        assertThat(sql).doesNotContain("'100.5'");
    }

    @Test
    @DisplayName("✅ Boolean values")
    void testBooleanValues() {
        // Given
        var query = OneirosQuery.select(TestEntity.class)
                .where("active").is(true);

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity WHERE active = true");
    }

    @Test
    @DisplayName("✅ Empty query - SELECT all")
    void testEmptyQuery() {
        // Given
        var query = OneirosQuery.select(TestEntity.class);

        // When
        String sql = query.toSql();

        // Then
        assertThat(sql).isEqualTo("SELECT * FROM test_entity");
    }

    // Test Entity
    @OneirosEntity("test_entity")
    static class TestEntity {
        String id;
        String name;
        int age;

        public TestEntity() {}

        public TestEntity(String id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }
    }
}
