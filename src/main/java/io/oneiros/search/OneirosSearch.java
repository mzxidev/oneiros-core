package io.oneiros.search;

import io.oneiros.client.OneirosClient;
import reactor.core.publisher.Flux;

/**
 * Fluent API for full-text search queries in SurrealDB.
 * Provides BM25 ranking, highlights, and custom analyzers.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * OneirosSearch.table(Product.class)
 *     .content("description", "title")
 *     .matching("wireless headphones")
 *     .withScoring()
 *     .minScore(0.7)
 *     .withHighlights()
 *     .limit(20)
 *     .execute(client);
 * }</pre>
 */
public class OneirosSearch<T> {

    private final Class<T> entityClass;
    private String[] fields;
    private String query;
    private boolean scoring = false;
    private double minScore = 0.0;
    private boolean highlights = false;
    private String orderByField = null;
    private String orderDirection = "DESC";
    private Integer limit = null;

    private OneirosSearch(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * Creates a new search builder for the specified entity class.
     *
     * @param entityClass The entity class to search
     * @param <T> The entity type
     * @return A new search builder
     */
    public static <T> OneirosSearch<T> table(Class<T> entityClass) {
        return new OneirosSearch<>(entityClass);
    }

    /**
     * Specifies the fields to search in.
     *
     * @param fields The field names to search
     * @return This builder
     */
    public OneirosSearch<T> content(String... fields) {
        this.fields = fields;
        return this;
    }

    /**
     * Specifies the search query text.
     *
     * @param query The search query
     * @return This builder
     */
    public OneirosSearch<T> matching(String query) {
        this.query = query;
        return this;
    }

    /**
     * Enables BM25 scoring.
     *
     * @return This builder
     */
    public OneirosSearch<T> withScoring() {
        this.scoring = true;
        return this;
    }

    /**
     * Sets minimum relevance score.
     *
     * @param minScore Minimum score (0.0 to 1.0)
     * @return This builder
     */
    public OneirosSearch<T> minScore(double minScore) {
        this.minScore = minScore;
        this.scoring = true;
        return this;
    }

    /**
     * Enables search result highlights.
     *
     * @return This builder
     */
    public OneirosSearch<T> withHighlights() {
        this.highlights = true;
        return this;
    }

    /**
     * Sets the ordering field.
     *
     * @param field The field to order by
     * @param direction "ASC" or "DESC"
     * @return This builder
     */
    public OneirosSearch<T> orderBy(String field, String direction) {
        this.orderByField = field;
        this.orderDirection = direction;
        return this;
    }

    /**
     * Limits the number of results.
     *
     * @param limit Maximum number of results
     * @return This builder
     */
    public OneirosSearch<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Executes the search query.
     *
     * @param client The Oneiros client
     * @return Flux of search results
     */
    public Flux<T> execute(OneirosClient client) {
        String sql = buildSQL();
        return client.query(sql, entityClass);
    }

    /**
     * Builds the SurrealQL search query.
     */
    private String buildSQL() {
        String tableName = entityClass.getSimpleName().toLowerCase();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT *");

        if (scoring) {
            sql.append(", search::score(1) AS relevance");
        }

        sql.append(" FROM ").append(tableName);

        if (fields != null && fields.length > 0 && query != null) {
            sql.append(" WHERE ");

            for (int i = 0; i < fields.length; i++) {
                if (i > 0) sql.append(" OR ");
                sql.append(fields[i]).append(" @@ '").append(escapeQuery(query)).append("'");
            }

            if (minScore > 0) {
                sql.append(" AND search::score(1) >= ").append(minScore);
            }
        }

        if (orderByField != null) {
            sql.append(" ORDER BY ").append(orderByField).append(" ").append(orderDirection);
        } else if (scoring) {
            sql.append(" ORDER BY relevance DESC");
        }

        if (limit != null) {
            sql.append(" LIMIT ").append(limit);
        }

        return sql.toString();
    }

    /**
     * Escapes special characters in search query.
     */
    private String escapeQuery(String query) {
        if (query == null) return "";
        return query.replace("'", "\\'");
    }
}
