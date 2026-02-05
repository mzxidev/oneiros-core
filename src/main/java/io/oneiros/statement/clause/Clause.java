package io.oneiros.statement.clause;

/**
 * Base interface for all SurrealQL clauses.
 *
 * Clauses alter the way a query is executed:
 * - WHERE: Filter results
 * - FROM: Specify target table(s)
 * - GROUP BY: Group results
 * - ORDER BY: Sort results
 * - LIMIT: Limit result count
 * - FETCH: Fetch related records
 * - OMIT: Omit fields
 * - SPLIT: Split into subqueries
 * - WITH: Use index iterator
 * - EXPLAIN: Show query plan
 */
public interface Clause {

    /**
     * Build the SQL fragment for this clause.
     */
    String toSql();
}
