package io.oneiros.live;

import io.oneiros.client.OneirosClient;
import reactor.core.publisher.Flux;

/**
 * Fluent API builder for LIVE SELECT queries.
 * Provides a chainable interface for building real-time subscriptions.
 *
 * Example:
 * <pre>
 * OneirosLive.from(Product.class, client)
 *     .where("price").lessThan(100)
 *     .where("category").is("electronics")
 *     .subscribe()
 *     .subscribe(event -> {
 *         if (event.isCreate()) {
 *             System.out.println("New product: " + event.getData());
 *         }
 *     });
 * </pre>
 */
public class OneirosLive<T> {

    private final OneirosClient client;
    private final OneirosLiveManager liveManager;
    private final Class<T> entityClass;
    private final String tableName;

    private StringBuilder whereClause = new StringBuilder();
    private boolean hasWhere = false;

    public OneirosLive(OneirosClient client, OneirosLiveManager liveManager, Class<T> entityClass, String tableName) {
        this.client = client;
        this.liveManager = liveManager;
        this.entityClass = entityClass;
        this.tableName = tableName;
    }

    /**
     * Creates a LIVE SELECT builder for the specified entity class.
     *
     * @param entityClass The entity class
     * @param client The Oneiros client
     * @param <T> The entity type
     * @return A new OneirosLive builder
     */
    public static <T> OneirosLive<T> from(Class<T> entityClass, OneirosClient client, OneirosLiveManager liveManager) {
        String tableName = extractTableName(entityClass);
        return new OneirosLive<>(client, liveManager, entityClass, tableName);
    }

    /**
     * Creates a LIVE SELECT builder for the specified table name.
     *
     * @param tableName The table name
     * @param entityClass The entity class
     * @param client The Oneiros client
     * @param <T> The entity type
     * @return A new OneirosLive builder
     */
    public static <T> OneirosLive<T> table(String tableName, Class<T> entityClass, OneirosClient client, OneirosLiveManager liveManager) {
        return new OneirosLive<>(client, liveManager, entityClass, tableName);
    }

    /**
     * Adds a WHERE condition to the LIVE SELECT query.
     *
     * @param field The field name
     * @return A WhereBuilder for chaining operators
     */
    public WhereBuilder where(String field) {
        return new WhereBuilder(field);
    }

    /**
     * Subscribes to the LIVE SELECT query and returns a Flux of events.
     *
     * @return Flux of OneirosEvent
     */
    public Flux<OneirosEvent<T>> subscribe() {
        String where = hasWhere ? whereClause.toString() : null;
        return liveManager.subscribe(tableName, entityClass, where);
    }

    /**
     * Builder for WHERE clause conditions.
     */
    public class WhereBuilder {
        private final String field;

        public WhereBuilder(String field) {
            this.field = field;
        }

        public OneirosLive<T> is(Object value) {
            addCondition(field + " = " + formatValue(value));
            return OneirosLive.this;
        }

        public OneirosLive<T> notEquals(Object value) {
            addCondition(field + " != " + formatValue(value));
            return OneirosLive.this;
        }

        public OneirosLive<T> greaterThan(Object value) {
            addCondition(field + " > " + formatValue(value));
            return OneirosLive.this;
        }

        public OneirosLive<T> greaterThanOrEqual(Object value) {
            addCondition(field + " >= " + formatValue(value));
            return OneirosLive.this;
        }

        public OneirosLive<T> lessThan(Object value) {
            addCondition(field + " < " + formatValue(value));
            return OneirosLive.this;
        }

        public OneirosLive<T> lessThanOrEqual(Object value) {
            addCondition(field + " <= " + formatValue(value));
            return OneirosLive.this;
        }

        public OneirosLive<T> in(Object... values) {
            StringBuilder inClause = new StringBuilder(field + " IN [");
            for (int i = 0; i < values.length; i++) {
                if (i > 0) inClause.append(", ");
                inClause.append(formatValue(values[i]));
            }
            inClause.append("]");
            addCondition(inClause.toString());
            return OneirosLive.this;
        }

        public OneirosLive<T> like(String pattern) {
            addCondition(field + " ~ '" + pattern + "'");
            return OneirosLive.this;
        }

        public OneirosLive<T> isNull() {
            addCondition(field + " IS NULL");
            return OneirosLive.this;
        }

        public OneirosLive<T> isNotNull() {
            addCondition(field + " IS NOT NULL");
            return OneirosLive.this;
        }

        private void addCondition(String condition) {
            if (hasWhere) {
                whereClause.append(" AND ");
            }
            whereClause.append(condition);
            hasWhere = true;
        }
    }

    /**
     * Formats a value for SQL.
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        } else if (value instanceof String) {
            return "'" + value.toString().replace("'", "\\'") + "'";
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Number) {
            return value.toString();
        } else {
            return "'" + value.toString().replace("'", "\\'") + "'";
        }
    }

    /**
     * Extracts the table name from an entity class.
     */
    private static <T> String extractTableName(Class<T> entityClass) {
        io.oneiros.annotation.OneirosEntity annotation =
            entityClass.getAnnotation(io.oneiros.annotation.OneirosEntity.class);

        if (annotation != null && !annotation.value().isBlank()) {
            return annotation.value();
        }

        return entityClass.getSimpleName().toLowerCase();
    }
}
