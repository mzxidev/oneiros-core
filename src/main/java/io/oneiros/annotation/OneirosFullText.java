package io.oneiros.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for full-text search indexing.
 * The migration engine will automatically create a FULLTEXT index for this field.
 *
 * Example:
 * <pre>
 * {@literal @}OneirosEntity("articles")
 * public class Article {
 *     {@literal @}OneirosID
 *     private String id;
 *
 *     {@literal @}OneirosFullText(analyzer = "ascii")
 *     private String content;
 * }
 * </pre>
 *
 * Generated SQL:
 * <pre>
 * DEFINE ANALYZER ascii TOKENIZERS blank, class FILTERS lowercase, ascii;
 * DEFINE INDEX idx_article_content ON articles FIELDS content
 *   SEARCH ANALYZER ascii BM25 HIGHLIGHTS;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneirosFullText {

    /**
     * The analyzer to use for tokenization and filtering.
     * Common values: "ascii", "english", "german", "french", etc.
     * Default: "ascii"
     */
    String analyzer() default "ascii";

    /**
     * Whether to enable BM25 ranking (best match ranking algorithm).
     * Default: true
     */
    boolean bm25() default true;

    /**
     * Whether to enable highlights in search results.
     * Default: true
     */
    boolean highlights() default true;

    /**
     * Custom index name. If not specified, will be auto-generated as "idx_{table}_{field}".
     */
    String indexName() default "";
}
