package io.oneiros.query;

import io.oneiros.client.OneirosClient;
import reactor.core.publisher.Flux;

/**
 * Fluent API builder for full-text search queries.
 * Provides an intuitive interface for searching text content in SurrealDB.
 *
 * Example:
 * <pre>
 * OneirosSearch.in(Article.class, client)
 *     .content("description")
 *     .matching("machine learning")
 *     .withScoring()
 *     .withHighlights()
 *     .limit(10)
 *     .execute()
 *     .subscribe(result -> {
 *         System.out.println("Score: " + result.getScore());
 *         System.out.println("Highlights: " + result.getHighlights());
 *         System.out.println("Article: " + result.getData());
 *     });
 * </pre>
 */
public class OneirosSearch<T> {

    private final OneirosClient client;
    private final Class<T> entityClass;
    private final String tableName;

    private String searchField;
    private String searchTerm;
    private boolean withScoring = false;
    private boolean withHighlights = false;
    private Integer limit;
    private String whereClause;

    public OneirosSearch(OneirosClient client, Class<T> entityClass, String tableName) {
        this.client = client;
        this.entityClass = entityClass;
        this.tableName = tableName;
    }

    /**
     * Creates a search builder for the specified entity class.
     */
    public static <T> OneirosSearch<T> in(Class<T> entityClass, OneirosClient client) {
        String tableName = extractTableName(entityClass);
        return new OneirosSearch<>(client, entityClass, tableName);
    }

    /**
     * Creates a search builder for the specified table name.
     */
    public static <T> OneirosSearch<T> table(String tableName, Class<T> entityClass, OneirosClient client) {
        return new OneirosSearch<>(client, entityClass, tableName);
    }

    /**
     * Specifies the field to search in.
     */
    public OneirosSearch<T> content(String field) {
        this.searchField = field;
        return this;
    }

    /**
     * Specifies the search term/query.
     */
    public OneirosSearch<T> matching(String term) {
        this.searchTerm = term;
        return this;
    }

    /**
     * Enables BM25 scoring in the results.
     */
    public OneirosSearch<T> withScoring() {
        this.withScoring = true;
        return this;
    }

    /**
     * Enables highlights in the results.
     */
    public OneirosSearch<T> withHighlights() {
        this.withHighlights = true;
        return this;
    }

    /**
     * Limits the number of results.
     */
    public OneirosSearch<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Adds a WHERE clause to filter results.
     */
    public OneirosSearch<T> where(String condition) {
        this.whereClause = condition;
        return this;
    }

    /**
     * Executes the search query and returns results.
     */
    public Flux<SearchResult<T>> execute() {
        String sql = buildSearchSql();

        return client.query(sql, SearchResultInternal.class)
            .map(internal -> new SearchResult<>(
                internal.id,
                convertToEntity(internal.data),
                internal.score,
                internal.highlights
            ));
    }

    /**
     * Builds the SurrealQL search query.
     */
    private String buildSearchSql() {
        if (searchField == null || searchTerm == null) {
            throw new IllegalStateException("Both content() and matching() must be specified");
        }

        StringBuilder sql = new StringBuilder("SELECT id, *");

        if (withScoring) {
            sql.append(", search::score(1) AS score");
        }

        if (withHighlights) {
            sql.append(", search::highlight('<b>', '</b>', 1) AS highlights");
        }

        sql.append(" FROM ").append(tableName);
        sql.append(" WHERE ").append(searchField).append(" @1@ '").append(escapeSql(searchTerm)).append("'");

        if (whereClause != null && !whereClause.isBlank()) {
            sql.append(" AND ").append(whereClause);
        }

        if (withScoring) {
            sql.append(" ORDER BY score DESC");
        }

        if (limit != null) {
            sql.append(" LIMIT ").append(limit);
        }

        return sql.toString();
    }

    /**
     * Converts internal search result to entity.
     */
    @SuppressWarnings("unchecked")
    private T convertToEntity(Object data) {
        if (data == null) {
            return null;
        }

        try {
            if (entityClass.isInstance(data)) {
                return (T) data;
            }

            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert search result to entity", e);
        }
    }

    /**
     * Escapes SQL special characters.
     */
    private String escapeSql(String value) {
        return value.replace("'", "\\'");
    }

    /**
     * Extracts table name from entity class.
     */
    private static <T> String extractTableName(Class<T> entityClass) {
        io.oneiros.annotation.OneirosEntity annotation =
            entityClass.getAnnotation(io.oneiros.annotation.OneirosEntity.class);

        if (annotation != null && !annotation.value().isBlank()) {
            return annotation.value();
        }

        return entityClass.getSimpleName().toLowerCase();
    }

    /**
     * Internal class for deserializing search results.
     */
    private static class SearchResultInternal {
        public String id;
        public Object data;
        public Double score;
        public String highlights;
    }

    /**
     * Search result wrapper containing score and highlights.
     */
    public static class SearchResult<T> {
        private final String id;
        private final T data;
        private final Double score;
        private final String highlights;

        public SearchResult(String id, T data, Double score, String highlights) {
            this.id = id;
            this.data = data;
            this.score = score;
            this.highlights = highlights;
        }

        public String getId() {
            return id;
        }

        public T getData() {
            return data;
        }

        public Double getScore() {
            return score;
        }

        public String getHighlights() {
            return highlights;
        }

        public boolean hasScore() {
            return score != null;
        }

        public boolean hasHighlights() {
            return highlights != null && !highlights.isBlank();
        }

        @Override
        public String toString() {
            return "SearchResult{" +
                    "id='" + id + '\'' +
                    ", score=" + score +
                    ", hasHighlights=" + hasHighlights() +
                    ", data=" + data +
                    '}';
        }
    }
}
