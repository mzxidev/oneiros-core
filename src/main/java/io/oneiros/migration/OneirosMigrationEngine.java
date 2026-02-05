package io.oneiros.migration;

import io.oneiros.annotation.*;
import io.oneiros.client.OneirosClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Auto-migration engine for SurrealDB schema definition.
 * Scans classpath for @OneirosEntity classes and generates DEFINE statements.
 *
 * <p>Features:
 * <ul>
 *   <li>DEFINE TABLE with SCHEMAFULL/SCHEMALESS based on @OneirosTable</li>
 *   <li>DEFINE FIELD with types, constraints, and assertions</li>
 *   <li>DEFINE INDEX for unique and indexed fields</li>
 *   <li>Record link validation via @OneirosRelation</li>
 *   <li>History table creation for @OneirosVersioned entities</li>
 * </ul>
 */
@Slf4j
public class OneirosMigrationEngine {

    private final OneirosClient client;
    private final String basePackage;
    private final boolean autoMigrate;
    private final boolean dryRun;

    public OneirosMigrationEngine(OneirosClient client, String basePackage) {
        this(client, basePackage, true, false);
    }

    public OneirosMigrationEngine(OneirosClient client, String basePackage, boolean autoMigrate, boolean dryRun) {
        this.client = client;
        this.basePackage = basePackage;
        this.autoMigrate = autoMigrate;
        this.dryRun = dryRun;
    }

    /**
     * Scan and execute migrations for all @OneirosEntity classes.
     *
     * @return Mono<Void> indicating completion
     */
    public Mono<Void> migrate() {
        if (!autoMigrate) {
            log.info("🔧 Auto-migration disabled, skipping schema generation");
            return Mono.empty();
        }

        log.info("🔍 Scanning for @OneirosEntity classes in: {}", basePackage);

        return Mono.fromCallable(this::scanEntities)
            .flatMapMany(Flux::fromIterable)
            .flatMap(this::generateSchemaForEntity)
            .flatMap(sql -> {
                if (dryRun) {
                    log.info("📝 [DRY-RUN] Would execute: {}", sql);
                    return Mono.empty();
                } else {
                    log.debug("📤 Executing: {}", sql);
                    return client.query(sql, Object.class).then();
                }
            })
            .then()
            .doOnSuccess(v -> log.info("✅ Migration completed successfully"))
            .doOnError(e -> log.error("❌ Migration failed", e));
    }

    /**
     * Scan classpath for entity classes.
     */
    private Set<Class<?>> scanEntities() {
        Set<Class<?>> entities = new HashSet<>();

        try {
            ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(OneirosEntity.class));

            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);

            for (BeanDefinition bd : candidates) {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                entities.add(clazz);
                log.debug("🔎 Found entity: {}", clazz.getSimpleName());
            }
        } catch (Exception e) {
            log.error("Failed to scan entities", e);
        }

        return entities;
    }

    /**
     * Generate all schema statements for a single entity.
     */
    private Flux<String> generateSchemaForEntity(Class<?> entityClass) {
        List<String> statements = new ArrayList<>();

        try {
            OneirosEntity entityAnnotation = entityClass.getAnnotation(OneirosEntity.class);
            String tableName = entityAnnotation.value().isEmpty()
                ? entityClass.getSimpleName().toLowerCase()
                : entityAnnotation.value();

            log.info("🏗️ Generating schema for table: {}", tableName);

            // 1. DEFINE TABLE
            statements.add(generateDefineTable(entityClass, tableName));

            // 2. DEFINE FIELD for each field
            for (Field field : entityClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(OneirosID.class)) {
                    continue; // Skip ID field
                }

                String fieldDef = generateDefineField(field, tableName);
                if (fieldDef != null) {
                    statements.add(fieldDef);
                }
            }

            // 3. DEFINE INDEX for unique/indexed fields
            for (Field field : entityClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(OneirosField.class)) {
                    OneirosField fieldAnnotation = field.getAnnotation(OneirosField.class);
                    if (fieldAnnotation.unique() || fieldAnnotation.index()) {
                        statements.add(generateDefineIndex(field, tableName, fieldAnnotation));
                    }
                }

                // Full-text search indexes
                if (field.isAnnotationPresent(OneirosFullText.class)) {
                    statements.addAll(generateFullTextIndex(field, tableName));
                }
            }

            // 4. History table for @OneirosVersioned
            if (entityClass.isAnnotationPresent(OneirosVersioned.class)) {
                statements.addAll(generateVersionedSchema(entityClass, tableName));
            }

        } catch (Exception e) {
            log.error("Failed to generate schema for {}", entityClass.getSimpleName(), e);
        }

        return Flux.fromIterable(statements);
    }

    /**
     * Generate DEFINE TABLE statement.
     */
    private String generateDefineTable(Class<?> entityClass, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("DEFINE TABLE ").append(tableName);

        OneirosTable tableAnnotation = entityClass.getAnnotation(OneirosTable.class);

        if (tableAnnotation != null) {
            sql.append(" TYPE ").append(tableAnnotation.type());

            if (tableAnnotation.isStrict()) {
                sql.append(" SCHEMAFULL");
            } else {
                sql.append(" SCHEMALESS");
            }

            if (!tableAnnotation.comment().isEmpty()) {
                sql.append(" COMMENT '").append(escapeString(tableAnnotation.comment())).append("'");
            }

            if (!tableAnnotation.changeFeed().isEmpty()) {
                sql.append(" CHANGEFEED ").append(tableAnnotation.changeFeed());
            }
        } else {
            sql.append(" SCHEMALESS");
        }

        return sql.toString();
    }

    /**
     * Generate DEFINE FIELD statement.
     */
    private String generateDefineField(Field field, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("DEFINE FIELD ").append(field.getName());
        sql.append(" ON TABLE ").append(tableName);

        String surrealType = null;
        String defaultValue = null;
        boolean readonly = false;
        String assertion = null;
        String comment = null;

        // Check @OneirosField annotation
        if (field.isAnnotationPresent(OneirosField.class)) {
            OneirosField fieldAnnotation = field.getAnnotation(OneirosField.class);
            surrealType = fieldAnnotation.type().isEmpty() ? null : fieldAnnotation.type();
            defaultValue = fieldAnnotation.defaultValue().isEmpty() ? null : fieldAnnotation.defaultValue();
            readonly = fieldAnnotation.readonly();
            assertion = fieldAnnotation.assertion().isEmpty() ? null : fieldAnnotation.assertion();
            comment = fieldAnnotation.comment().isEmpty() ? null : fieldAnnotation.comment();
        }

        // Check @OneirosRelation annotation
        if (field.isAnnotationPresent(OneirosRelation.class)) {
            OneirosRelation relationAnnotation = field.getAnnotation(OneirosRelation.class);
            surrealType = inferRelationType(field, relationAnnotation);
            assertion = generateRelationAssertion(relationAnnotation);
        }

        // Infer type if not specified
        if (surrealType == null) {
            surrealType = inferSurrealType(field);
        }

        if (surrealType != null) {
            sql.append(" TYPE ").append(surrealType);
        }

        if (defaultValue != null) {
            sql.append(" DEFAULT ").append(defaultValue);
        }

        if (readonly) {
            sql.append(" READONLY");
        }

        if (assertion != null) {
            sql.append(" ASSERT ").append(assertion);
        }

        if (comment != null) {
            sql.append(" COMMENT '").append(escapeString(comment)).append("'");
        }

        return sql.toString();
    }

    /**
     * Generate DEFINE INDEX statement.
     */
    private String generateDefineIndex(Field field, String tableName, OneirosField fieldAnnotation) {
        StringBuilder sql = new StringBuilder();

        String indexName = fieldAnnotation.indexName().isEmpty()
            ? "idx_" + tableName + "_" + field.getName()
            : fieldAnnotation.indexName();

        sql.append("DEFINE INDEX ").append(indexName);
        sql.append(" ON TABLE ").append(tableName);
        sql.append(" FIELDS ").append(field.getName());

        if (fieldAnnotation.unique()) {
            sql.append(" UNIQUE");
        }

        return sql.toString();
    }

    /**
     * Generate schema for versioned entities.
     */
    private List<String> generateVersionedSchema(Class<?> entityClass, String tableName) {
        List<String> statements = new ArrayList<>();

        OneirosVersioned versionAnnotation = entityClass.getAnnotation(OneirosVersioned.class);
        String historyTable = versionAnnotation.historyTable().isEmpty()
            ? tableName + "_history"
            : versionAnnotation.historyTable();

        log.info("📜 Creating history table: {}", historyTable);

        // Create history table
        StringBuilder sql = new StringBuilder();
        sql.append("DEFINE TABLE ").append(historyTable).append(" SCHEMALESS");
        statements.add(sql.toString());

        // Create event to track changes
        sql = new StringBuilder();
        sql.append("DEFINE EVENT ").append(tableName).append("_history_event");
        sql.append(" ON TABLE ").append(tableName);
        sql.append(" WHEN $event = 'UPDATE' OR $event = 'DELETE'");
        sql.append(" THEN {");
        sql.append(" CREATE ").append(historyTable).append(" SET ");
        sql.append("record_id = $before.id, ");
        sql.append("event_type = $event, ");
        sql.append("timestamp = time::now(), ");

        if (versionAnnotation.includeMetadata()) {
            sql.append("metadata = { user: $auth.id, session: $session }, ");
        }

        if (versionAnnotation.fullSnapshots()) {
            sql.append("data = $before");
        } else {
            sql.append("diff = $diff");
        }

        sql.append(" }");
        statements.add(sql.toString());

        return statements;
    }

    /**
     * Infer SurrealDB type from Java type.
     */
    private String inferSurrealType(Field field) {
        Class<?> type = field.getType();

        if (type == String.class) return "string";
        if (type == Integer.class || type == int.class) return "int";
        if (type == Long.class || type == long.class) return "int";
        if (type == Double.class || type == double.class) return "float";
        if (type == Float.class || type == float.class) return "float";
        if (type == Boolean.class || type == boolean.class) return "bool";
        if (type == java.time.LocalDateTime.class) return "datetime";
        if (type == java.time.LocalDate.class) return "datetime";
        if (type == java.time.Instant.class) return "datetime";
        if (type == java.util.UUID.class) return "uuid";
        if (type == java.time.Duration.class) return "duration";

        if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericType;
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0) {
                    String elementType = inferTypeFromClass((Class<?>) typeArgs[0]);
                    return "array<" + elementType + ">";
                }
            }
            return "array";
        }

        if (Map.class.isAssignableFrom(type)) {
            return "object";
        }

        return "any";
    }

    private String inferTypeFromClass(Class<?> clazz) {
        if (clazz == String.class) return "string";
        if (clazz == Integer.class) return "int";
        if (clazz == Long.class) return "int";
        if (clazz == Double.class || clazz == Float.class) return "float";
        if (clazz == Boolean.class) return "bool";
        return "any";
    }

    /**
     * Infer relation type from @OneirosRelation.
     */
    private String inferRelationType(Field field, OneirosRelation relationAnnotation) {
        Class<?> target = relationAnnotation.target();
        String targetTable = getTableName(target);

        switch (relationAnnotation.type()) {
            case ONE_TO_ONE:
                return "record<" + targetTable + ">";
            case ONE_TO_MANY:
            case MANY_TO_MANY:
                return "array<record<" + targetTable + ">>";
            default:
                return "record<" + targetTable + ">";
        }
    }

    /**
     * Generate assertion for relation validation.
     */
    private String generateRelationAssertion(OneirosRelation relationAnnotation) {
        Class<?> target = relationAnnotation.target();
        String targetTable = getTableName(target);

        switch (relationAnnotation.type()) {
            case ONE_TO_ONE:
                return "$value.type() = '" + targetTable + "'";
            case ONE_TO_MANY:
            case MANY_TO_MANY:
                return "array::all($value, |$v| $v.type() = '" + targetTable + "')";
            default:
                return null;
        }
    }

    private String getTableName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(OneirosEntity.class)) {
            String value = clazz.getAnnotation(OneirosEntity.class).value();
            return value.isEmpty() ? clazz.getSimpleName().toLowerCase() : value;
        }
        return clazz.getSimpleName().toLowerCase();
    }

    /**
     * Generate DEFINE ANALYZER and DEFINE INDEX for full-text search.
     */
    private List<String> generateFullTextIndex(Field field, String tableName) {
        List<String> statements = new ArrayList<>();

        OneirosFullText ftAnnotation = field.getAnnotation(OneirosFullText.class);
        String analyzer = ftAnnotation.analyzer();
        String fieldName = field.getName();

        String indexName = ftAnnotation.indexName().isEmpty()
            ? "idx_" + tableName + "_" + fieldName + "_fts"
            : ftAnnotation.indexName();

        log.info("🔍 Creating full-text search index: {} on {}.{}", indexName, tableName, fieldName);

        // 1. DEFINE ANALYZER (only if not 'ascii' which is built-in)
        if (!"ascii".equals(analyzer)) {
            StringBuilder analyzerSql = new StringBuilder();
            analyzerSql.append("DEFINE ANALYZER ").append(analyzer);
            analyzerSql.append(" TOKENIZERS blank, class");
            analyzerSql.append(" FILTERS lowercase, ").append(analyzer);
            statements.add(analyzerSql.toString());
        }

        // 2. DEFINE INDEX with SEARCH
        StringBuilder indexSql = new StringBuilder();
        indexSql.append("DEFINE INDEX ").append(indexName);
        indexSql.append(" ON TABLE ").append(tableName);
        indexSql.append(" FIELDS ").append(fieldName);
        indexSql.append(" SEARCH ANALYZER ").append(analyzer);

        if (ftAnnotation.bm25()) {
            indexSql.append(" BM25");
        }

        if (ftAnnotation.highlights()) {
            indexSql.append(" HIGHLIGHTS");
        }

        statements.add(indexSql.toString());

        return statements;
    }
    private String escapeString(String s) {
        return s.replace("'", "\\'");
    }
}
