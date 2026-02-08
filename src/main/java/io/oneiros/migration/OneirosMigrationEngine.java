package io.oneiros.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oneiros.annotation.*;
import io.oneiros.client.OneirosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Production-ready migration engine for SurrealDB.
 *
 * <p>Features two migration strategies:
 * <ol>
 *   <li><b>Versioned Migrations (Flyway-style):</b> Execute {@link OneirosMigration} implementations
 *       in version order. Track applied migrations in {@code oneiros_schema_history}.</li>
 *   <li><b>Schema Sync:</b> Auto-generate DEFINE statements from {@link OneirosTable} annotations
 *       to ensure tables exist with correct structure.</li>
 * </ol>
 *
 * <p><b>Execution Order:</b>
 * <ol>
 *   <li>Create {@code oneiros_schema_history} table (if not exists)</li>
 *   <li>Execute pending versioned migrations (in order)</li>
 *   <li>Sync schema definitions from {@code @OneirosTable} classes</li>
 * </ol>
 *
 * <p>This engine is framework-agnostic and uses ClassGraph for classpath scanning.
 *
 * @see OneirosMigration
 * @see OneirosTable
 */
public class OneirosMigrationEngine {

    private static final Logger log = LoggerFactory.getLogger(OneirosMigrationEngine.class);
    private static final String HISTORY_TABLE = "oneiros_schema_history";

    private final OneirosClient client;
    private final String basePackage;
    private final boolean autoMigrate;
    private final boolean dryRun;
    private final boolean overwrite;
    private final ObjectMapper objectMapper;

    public OneirosMigrationEngine(OneirosClient client, String basePackage) {
        this(client, basePackage, true, false, false);
    }

    public OneirosMigrationEngine(OneirosClient client, String basePackage, boolean autoMigrate, boolean dryRun) {
        this(client, basePackage, autoMigrate, dryRun, false);
    }

    public OneirosMigrationEngine(OneirosClient client, String basePackage, boolean autoMigrate, boolean dryRun, boolean overwrite) {
        this.client = client;
        this.basePackage = basePackage;
        this.autoMigrate = autoMigrate;
        this.dryRun = dryRun;
        this.overwrite = overwrite;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Execute the complete migration process:
     * 1. Setup schema history table
     * 2. Run versioned migrations
     * 3. Sync schema definitions
     *
     * @return Mono<Void> indicating completion
     */
    public Mono<Void> migrate() {
        if (!autoMigrate) {
            log.info("🔧 Auto-migration disabled, skipping");
            return Mono.empty();
        }

        log.info("🚀 Starting Oneiros Migration Engine");
        log.info("📦 Base package: {}", basePackage);

        return createSchemaHistoryTable()
            .then(runVersionedMigrations())
            .then(syncSchemaDefinitions())
            .doOnSuccess(v -> log.info("✅ Migration completed successfully"))
            .doOnError(e -> log.error("❌ Migration failed", e));
    }

    /**
     * Step 1: Create the schema history table if it doesn't exist.
     */
    private Mono<Void> createSchemaHistoryTable() {
        log.debug("📋 Creating schema history table...");

        String sql = String.format(
            "DEFINE TABLE IF NOT EXISTS %s SCHEMAFULL; " +
            "DEFINE FIELD IF NOT EXISTS version ON %s TYPE int; " +
            "DEFINE FIELD IF NOT EXISTS description ON %s TYPE string; " +
            "DEFINE FIELD IF NOT EXISTS installed_on ON %s TYPE datetime; " +
            "DEFINE FIELD IF NOT EXISTS execution_time_ms ON %s TYPE int; " +
            "DEFINE FIELD IF NOT EXISTS success ON %s TYPE bool; " +
            "DEFINE FIELD IF NOT EXISTS error_message ON %s TYPE option<string>; " +
            "DEFINE INDEX IF NOT EXISTS idx_version ON %s FIELDS version UNIQUE;",
            HISTORY_TABLE, HISTORY_TABLE, HISTORY_TABLE, HISTORY_TABLE,
            HISTORY_TABLE, HISTORY_TABLE, HISTORY_TABLE, HISTORY_TABLE
        );

        if (dryRun) {
            log.info("📝 [DRY-RUN] Would create schema history table");
            return Mono.empty();
        }

        return client.query(sql, Object.class)
            .then()
            .doOnSuccess(v -> log.debug("✅ Schema history table ready"))
            .onErrorResume(e -> {
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    log.debug("⏭️ Schema history table already exists");
                    return Mono.empty();
                }
                return Mono.error(e);
            });
    }

    /**
     * Step 2: Execute versioned migrations (Flyway-style).
     */
    private Mono<Void> runVersionedMigrations() {
        log.info("🔍 Scanning for versioned migrations...");

        return getCurrentDatabaseVersion()
            .flatMap(currentVersion -> {
                log.info("📊 Current database version: {}", currentVersion);

                // Scan for OneirosMigration implementations
                Set<OneirosMigration> migrations = scanMigrations();

                if (migrations.isEmpty()) {
                    log.info("📦 No versioned migrations found");
                    return Mono.empty();
                }

                // Filter and sort pending migrations
                List<OneirosMigration> pendingMigrations = migrations.stream()
                    .filter(m -> m.getVersion() > currentVersion)
                    .sorted(Comparator.comparingInt(OneirosMigration::getVersion))
                    .collect(Collectors.toList());

                if (pendingMigrations.isEmpty()) {
                    log.info("✅ All migrations up to date (version: {})", currentVersion);
                    return Mono.empty();
                }

                log.info("📋 Found {} pending migrations", pendingMigrations.size());
                pendingMigrations.forEach(m ->
                    log.info("   - V{}: {}", String.format("%03d", m.getVersion()), m.getDescription())
                );

                // Execute migrations sequentially
                return Flux.fromIterable(pendingMigrations)
                    .concatMap(this::executeMigration)
                    .then();
            });
    }

    /**
     * Get the current database version from schema history.
     */
    private Mono<Integer> getCurrentDatabaseVersion() {
        if (dryRun) {
            return Mono.just(0);
        }

        String query = String.format(
            "SELECT * FROM %s WHERE success = true ORDER BY version DESC LIMIT 1",
            HISTORY_TABLE
        );

        return client.query(query, SchemaHistoryEntry.class)
            .collectList()
            .map(entries -> {
                if (entries.isEmpty()) {
                    return 0;
                }
                return entries.get(0).getVersion();
            })
            .onErrorResume(e -> {
                // Table might not exist yet or be empty
                log.debug("No migration history found, starting from version 0");
                return Mono.just(0);
            });
    }

    /**
     * Scan classpath for OneirosMigration implementations.
     */
    private Set<OneirosMigration> scanMigrations() {
        ClasspathEntityScanner scanner = new ClasspathEntityScanner();
        Set<Class<?>> classes = scanner.scanForType(basePackage, OneirosMigration.class);

        Set<OneirosMigration> migrations = new HashSet<>();
        for (Class<?> clazz : classes) {
            try {
                if (!clazz.isInterface() && OneirosMigration.class.isAssignableFrom(clazz)) {
                    OneirosMigration migration = (OneirosMigration) clazz.getDeclaredConstructor().newInstance();
                    migrations.add(migration);
                    log.debug("📦 Found migration: V{} - {}",
                        String.format("%03d", migration.getVersion()),
                        migration.getDescription());
                }
            } catch (Exception e) {
                log.warn("⚠️ Failed to instantiate migration: {}", clazz.getName(), e);
            }
        }

        return migrations;
    }

    /**
     * Execute a single migration and record it in history.
     */
    private Mono<Void> executeMigration(OneirosMigration migration) {
        log.info("🔨 Executing migration V{}: {}",
            String.format("%03d", migration.getVersion()),
            migration.getDescription());

        if (dryRun) {
            log.info("📝 [DRY-RUN] Would execute migration V{}", migration.getVersion());
            return Mono.empty();
        }

        SchemaHistoryEntry entry = new SchemaHistoryEntry(
            migration.getVersion(),
            migration.getDescription()
        );

        long startTime = System.currentTimeMillis();

        return migration.up(client)
            .then(Mono.defer(() -> {
                // Migration successful
                long executionTime = System.currentTimeMillis() - startTime;
                entry.setExecutionTimeMs(executionTime);
                entry.setSuccess(true);

                log.info("✅ Migration V{} completed in {}ms",
                    String.format("%03d", migration.getVersion()),
                    executionTime);

                return recordMigration(entry);
            }))
            .onErrorResume(e -> {
                // Migration failed
                long executionTime = System.currentTimeMillis() - startTime;
                entry.setExecutionTimeMs(executionTime);
                entry.setSuccess(false);
                entry.setErrorMessage(e.getMessage());

                log.error("❌ Migration V{} failed after {}ms",
                    String.format("%03d", migration.getVersion()),
                    executionTime, e);

                return recordMigration(entry)
                    .then(Mono.error(new RuntimeException(
                        "Migration V" + migration.getVersion() + " failed: " + e.getMessage(), e)));
            });
    }

    /**
     * Record a migration in the schema history table.
     */
    private Mono<Void> recordMigration(SchemaHistoryEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            String query = String.format("CREATE %s CONTENT %s", HISTORY_TABLE, json);

            return client.query(query, Object.class)
                .then()
                .doOnSuccess(v -> log.debug("📝 Recorded migration history: V{}", entry.getVersion()));
        } catch (Exception e) {
            log.error("Failed to record migration history", e);
            return Mono.error(e);
        }
    }

    /**
     * Step 3: Sync schema definitions from @OneirosTable annotations.
     */
    private Mono<Void> syncSchemaDefinitions() {
        log.info("🔍 Scanning for @OneirosTable classes for schema sync...");

        // Scan entities ONCE at the start (blocking operation)
        Set<Class<?>> entities = scanEntities();

        if (entities.isEmpty()) {
            log.info("📦 No @OneirosTable classes found for schema sync");
            return Mono.empty();
        }

        log.info("📦 Found {} entities to sync", entities.size());

        // Convert to sequential reactive stream:
        // entities -> Flux -> concatMap (sequential) -> execute one by one
        return Flux.fromIterable(entities)
            .concatMap(entityClass -> {
                // For each entity, generate ALL its schema statements first
                log.debug("🔨 Syncing schema for: {}", entityClass.getSimpleName());
                return generateSchemaForEntity(entityClass)
                    .collectList()  // Collect all statements for this entity
                    .flatMapMany(statements -> {
                        // Now execute statements for this entity sequentially
                        log.debug("📋 {} statements to execute for {}",
                            statements.size(), entityClass.getSimpleName());
                        return Flux.fromIterable(statements);
                    })
                    .concatMap(sql -> executeStatement(sql));  // Execute one by one
            })
            .then()
            .doOnSuccess(v -> log.info("✅ Schema sync completed"));
    }

    /**
     * Execute a single SQL statement with error handling.
     * This method handles "already exists" errors gracefully and provides
     * helpful error messages for common issues.
     *
     * @param sql The SQL statement to execute
     * @return Mono<Void> that completes when the statement is executed
     */
    private Mono<Void> executeStatement(String sql) {
        if (dryRun) {
            log.info("📝 [DRY-RUN] Would execute: {}", sql);
            return Mono.empty();
        }

        log.debug("📤 Executing: {}", sql);

        return client.query(sql, Object.class)
            .then()
            .doOnSuccess(v -> log.debug("✅ Executed: {}", extractElementName(sql)))
            .onErrorResume(e -> {
                String msg = e.getMessage();

                // Handle "already exists" gracefully
                if (msg != null && msg.contains("already exists")) {
                    if (!overwrite) {
                        log.warn("⚠️ Schema element already exists: {}. " +
                            "If you updated field assertions or types, set 'oneiros.migration.overwrite: true' " +
                            "in application.yml to update existing definitions.",
                            extractElementName(sql));
                    }
                    log.debug("⏭️ Skipping (already exists): {}", sql);
                    return Mono.empty();
                }

                // Check for common assertion errors that indicate stale schema
                if (msg != null && msg.contains("length()") && msg.contains("no such method")) {
                    log.error("❌ SurrealDB error indicates stale schema with old assertions. " +
                        "Please set 'oneiros.migration.overwrite: true' in application.yml " +
                        "or manually run: REMOVE TABLE <tablename>; in SurrealDB");
                }

                // Log the actual error and fail the migration
                log.error("❌ Failed to execute: {}", sql);
                log.error("   Error: {}", msg);
                return Mono.error(e);
            });
    }

    /**
     * Extract element name from DEFINE statement for logging.
     */
    private String extractElementName(String sql) {
        // Try to extract meaningful element name from SQL
        // e.g., "DEFINE TABLE users" -> "TABLE users"
        // e.g., "DEFINE FIELD email ON TABLE users" -> "FIELD email ON users"
        if (sql.startsWith("DEFINE ")) {
            String withoutDefine = sql.substring(7);
            int typeEnd = withoutDefine.indexOf(' ');
            if (typeEnd > 0) {
                String type = withoutDefine.substring(0, typeEnd);
                String rest = withoutDefine.substring(typeEnd + 1);
                // Remove IF NOT EXISTS / OVERWRITE
                rest = rest.replace("IF NOT EXISTS ", "").replace("OVERWRITE ", "");
                // Find first meaningful word
                int firstSpace = rest.indexOf(' ');
                if (firstSpace > 0) {
                    String name = rest.substring(0, firstSpace);
                    // Check for ON TABLE
                    int onTable = rest.indexOf(" ON TABLE ");
                    if (onTable > 0) {
                        String tableName = rest.substring(onTable + 10).split(" ")[0];
                        return type + " " + name + " ON " + tableName;
                    }
                    return type + " " + name;
                }
                return type + " " + rest.split(" ")[0];
            }
        }
        return sql.length() > 50 ? sql.substring(0, 50) + "..." : sql;
    }

    /**
     * Scan classpath for entity classes using the framework-agnostic scanner.
     * Excludes test, demo, and example packages/classes from the oneiros-core library.
     */
    private Set<Class<?>> scanEntities() {
        ClasspathEntityScanner scanner = new ClasspathEntityScanner();
        return scanner.scan(basePackage);
    }

    /**
     * Generate all schema statements for a single entity.
     */
    private Flux<String> generateSchemaForEntity(Class<?> entityClass) {
        List<String> statements = new ArrayList<>();

        try {
            // Get table name from @OneirosTable or @OneirosEntity
            String tableName = getTableName(entityClass);

            // Check if this is a schema-only class (has @OneirosTable but no @OneirosEntity)
            boolean isSchemaOnly = entityClass.isAnnotationPresent(OneirosTable.class)
                && !entityClass.isAnnotationPresent(OneirosEntity.class);

            log.info("🏗️ Generating schema for table: {} (schema-only: {})", tableName, isSchemaOnly);

            // 1. DROP TABLE if requested (dev mode)
            OneirosTable tableAnnotation = entityClass.getAnnotation(OneirosTable.class);
            if (tableAnnotation != null && tableAnnotation.dropOnStart()) {
                log.warn("🗑️ Dropping table: {} (dropOnStart enabled)", tableName);
                statements.add("REMOVE TABLE IF EXISTS " + tableName);
            }

            // 2. DEFINE TABLE
            statements.add(generateDefineTable(entityClass, tableName));

            // 3. DEFINE FIELD for each field
            for (Field field : entityClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(OneirosID.class)) {
                    continue; // Skip ID field
                }

                String fieldDef = generateDefineField(field, tableName);
                if (fieldDef != null) {
                    statements.add(fieldDef);
                }
            }

            // 4. DEFINE INDEX for unique/indexed fields
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

            // 5. History table for @OneirosVersioned
            if (entityClass.isAnnotationPresent(OneirosVersioned.class)) {
                statements.addAll(generateVersionedSchema(entityClass, tableName));
            }

        } catch (Exception e) {
            log.error("Failed to generate schema for {}", entityClass.getSimpleName(), e);
        }

        return Flux.fromIterable(statements);
    }

    /**
     * Get table name from @OneirosTable or @OneirosEntity annotation.
     */
    private String getTableName(Class<?> entityClass) {
        OneirosTable tableAnnotation = entityClass.getAnnotation(OneirosTable.class);
        if (tableAnnotation != null && !tableAnnotation.value().isEmpty()) {
            return tableAnnotation.value();
        }

        OneirosEntity entityAnnotation = entityClass.getAnnotation(OneirosEntity.class);
        if (entityAnnotation != null && !entityAnnotation.value().isEmpty()) {
            return entityAnnotation.value();
        }

        // Fallback: lowercase class name
        return entityClass.getSimpleName().toLowerCase();
    }

    /**
     * Generate DEFINE TABLE statement.
     * Uses IF NOT EXISTS for idempotent migrations, or OVERWRITE to update existing definitions.
     */
    private String generateDefineTable(Class<?> entityClass, String tableName) {
        StringBuilder sql = new StringBuilder();
        if (overwrite) {
            sql.append("DEFINE TABLE OVERWRITE ").append(tableName);
        } else {
            sql.append("DEFINE TABLE IF NOT EXISTS ").append(tableName);
        }

        OneirosTable tableAnnotation = entityClass.getAnnotation(OneirosTable.class);

        if (tableAnnotation != null) {
            sql.append(" TYPE ").append(tableAnnotation.type());

            if (tableAnnotation.schemafull()) {
                sql.append(" SCHEMAFULL");
            } else {
                sql.append(" SCHEMALESS");
            }

            if (!tableAnnotation.changeFeed().isEmpty()) {
                sql.append(" CHANGEFEED ").append(tableAnnotation.changeFeed());
            }

            if (!tableAnnotation.comment().isEmpty()) {
                sql.append(" COMMENT '").append(escapeString(tableAnnotation.comment())).append("'");
            }

            // Add permissions (SurrealDB 2.x syntax)
            if (!tableAnnotation.permissions().isEmpty() && !"FULL".equals(tableAnnotation.permissions())) {
                sql.append(" PERMISSIONS ").append(tableAnnotation.permissions());
            }
        } else {
            sql.append(" SCHEMALESS");
        }

        return sql.toString();
    }

    /**
     * Generate DEFINE FIELD statement.
     * Uses IF NOT EXISTS for idempotent migrations, or OVERWRITE to update existing definitions.
     */
    private String generateDefineField(Field field, String tableName) {
        StringBuilder sql = new StringBuilder();
        if (overwrite) {
            sql.append("DEFINE FIELD OVERWRITE ").append(field.getName());
        } else {
            sql.append("DEFINE FIELD IF NOT EXISTS ").append(field.getName());
        }
        sql.append(" ON TABLE ").append(tableName);

        String surrealType = null;
        String defaultValue = null;
        boolean readonly = false;
        String assertion = null;
        String comment = null;
        boolean optional = false;

        // Check @OneirosField annotation
        if (field.isAnnotationPresent(OneirosField.class)) {
            OneirosField fieldAnnotation = field.getAnnotation(OneirosField.class);
            surrealType = fieldAnnotation.type().isEmpty() ? null : fieldAnnotation.type();
            defaultValue = fieldAnnotation.defaultValue().isEmpty() ? null : fieldAnnotation.defaultValue();
            readonly = fieldAnnotation.readonly();
            assertion = fieldAnnotation.assertion().isEmpty() ? null : fieldAnnotation.assertion();
            comment = fieldAnnotation.comment().isEmpty() ? null : fieldAnnotation.comment();
            optional = fieldAnnotation.optional();

            // Validate assertion for common mistakes
            if (assertion != null) {
                assertion = validateAndFixAssertion(assertion, field.getName(), tableName);
            }
        } else {
            // If no annotation, infer optional from whether the field type is a reference type (can be null)
            // Primitive types cannot be null, but their wrapper classes can
            optional = !field.getType().isPrimitive();
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

        // Wrap in option<> if the field is optional/nullable
        // Note: SurrealDB does not support option<any> or option<object> - these types already accept null values
        // This also applies to types containing 'any' like array<any>, set<any>, etc.
        // We need to check if the type IS 'any'/'object' OR CONTAINS 'any' anywhere in the type string
        boolean isAnyType = surrealType != null && containsAnyType(surrealType);
        boolean isObjectType = surrealType != null && "object".equals(surrealType);
        if (surrealType != null && optional && !surrealType.startsWith("option<") && !isAnyType && !isObjectType) {
            surrealType = "option<" + surrealType + ">";
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
     * Uses IF NOT EXISTS for idempotent migrations, or OVERWRITE to update existing definitions.
     */
    private String generateDefineIndex(Field field, String tableName, OneirosField fieldAnnotation) {
        StringBuilder sql = new StringBuilder();

        String indexName = fieldAnnotation.indexName().isEmpty()
            ? "idx_" + tableName + "_" + field.getName()
            : fieldAnnotation.indexName();

        if (overwrite) {
            sql.append("DEFINE INDEX OVERWRITE ").append(indexName);
        } else {
            sql.append("DEFINE INDEX IF NOT EXISTS ").append(indexName);
        }
        sql.append(" ON TABLE ").append(tableName);
        sql.append(" FIELDS ").append(field.getName());

        if (fieldAnnotation.unique()) {
            sql.append(" UNIQUE");
        }

        return sql.toString();
    }

    /**
     * Generate schema for versioned entities.
     * Uses IF NOT EXISTS for idempotent migrations (SurrealDB 1.x compatible).
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
        sql.append("DEFINE TABLE IF NOT EXISTS ").append(historyTable).append(" SCHEMALESS");
        statements.add(sql.toString());

        // Create event to track changes
        sql = new StringBuilder();
        sql.append("DEFINE EVENT IF NOT EXISTS ").append(tableName).append("_history_event");
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
        if (type == byte[].class) return "bytes";
        if (type == java.math.BigDecimal.class) return "decimal";
        if (type == java.math.BigInteger.class) return "int";
        if (type == Short.class || type == short.class) return "int";
        if (type == Byte.class || type == byte.class) return "int";

        // Handle List -> array<T>
        if (List.class.isAssignableFrom(type)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericType;
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    String elementType = inferTypeFromClass((Class<?>) typeArgs[0]);
                    return "array<" + elementType + ">";
                }
            }
            return "array";
        }

        // Handle Set -> set<T> (SurrealDB automatically deduplicates)
        if (Set.class.isAssignableFrom(type)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericType;
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    String elementType = inferTypeFromClass((Class<?>) typeArgs[0]);
                    return "set<" + elementType + ">";
                }
            }
            return "set";
        }

        // Handle Map/HashMap -> object
        // Note: SurrealDB 'object' type stores key-value pairs as JSON objects
        if (Map.class.isAssignableFrom(type)) {
            return "object";
        }

        // For enums, store as string
        if (type.isEnum()) {
            return "string";
        }

        // For unknown types, use 'object' which is more flexible
        // Note: When wrapped with option<>, it becomes option<object>
        // SurrealDB doesn't support option<any>, only concrete types
        return "object";
    }

    private String inferTypeFromClass(Class<?> clazz) {
        if (clazz == String.class) return "string";
        if (clazz == Integer.class || clazz == int.class) return "int";
        if (clazz == Long.class || clazz == long.class) return "int";
        if (clazz == Double.class || clazz == double.class) return "float";
        if (clazz == Float.class || clazz == float.class) return "float";
        if (clazz == Boolean.class || clazz == boolean.class) return "bool";
        if (clazz == java.time.LocalDateTime.class) return "datetime";
        if (clazz == java.time.LocalDate.class) return "datetime";
        if (clazz == java.time.Instant.class) return "datetime";
        if (clazz == java.util.UUID.class) return "uuid";
        if (clazz == java.time.Duration.class) return "duration";
        if (clazz == byte[].class) return "bytes";
        if (Number.class.isAssignableFrom(clazz)) return "number";
        if (Map.class.isAssignableFrom(clazz)) return "object";
        if (Set.class.isAssignableFrom(clazz)) return "set";
        if (List.class.isAssignableFrom(clazz)) return "array";
        if (clazz.isEnum()) return "string";
        // For unknown types, use 'object' which is more flexible
        // When wrapped with option<>, it becomes option<object>
        // SurrealDB doesn't support 'any', only concrete types
        return "object";
    }

    /**
     * Infer relation type from @OneirosRelation.
     */
    private String inferRelationType(Field field, OneirosRelation relationAnnotation) {
        Class<?> target = relationAnnotation.target();
        String targetTable = getTableName(target);

        return switch (relationAnnotation.type()) {
            case ONE_TO_ONE -> "record<" + targetTable + ">";
            case ONE_TO_MANY, MANY_TO_MANY -> "array<record<" + targetTable + ">>";
            default -> "record<" + targetTable + ">";
        };
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


    /**
     * Generate DEFINE ANALYZER and DEFINE INDEX for full-text search.
     * Uses IF NOT EXISTS for idempotent migrations (SurrealDB 1.x compatible).
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
            String analyzerSql = "DEFINE ANALYZER IF NOT EXISTS " + analyzer +
                    " TOKENIZERS blank, class" +
                    " FILTERS lowercase, " + analyzer;
            statements.add(analyzerSql);
        }

        // 2. DEFINE INDEX with SEARCH
        StringBuilder indexSql = new StringBuilder();
        indexSql.append("DEFINE INDEX IF NOT EXISTS ").append(indexName);
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

    /**
     * Validates and auto-fixes common assertion mistakes.
     * This helps users migrate from JavaScript-style methods to SurrealDB functions.
     *
     * <p>Auto-fixes:
     * <ul>
     *   <li>$value.length() → string::len($value)</li>
     *   <li>.trim() → string::trim()</li>
     *   <li>.toLowerCase() → string::lowercase()</li>
     *   <li>.toUpperCase() → string::uppercase()</li>
     *   <li>.isEmpty() → string::len($value) == 0</li>
     *   <li>.isBlank() → string::len(string::trim($value)) == 0</li>
     * </ul>
     *
     * @param assertion The original assertion
     * @param fieldName Field name for logging
     * @param tableName Table name for logging
     * @return The validated/fixed assertion
     */
    private String validateAndFixAssertion(String assertion, String fieldName, String tableName) {
        if (assertion == null || assertion.isEmpty()) {
            return assertion;
        }

        String original = assertion;
        boolean wasFixed = false;

        // Fix .length() -> string::len()
        // Pattern: $value.length() or $input.length() - handles both with and without surrounding context
        if (assertion.contains(".length()")) {
            assertion = assertion.replaceAll("\\$(\\w+)\\.length\\(\\)", "string::len(\\$$1)");
            wasFixed = true;
        }

        // Fix .trim() -> string::trim()
        if (assertion.contains(".trim()")) {
            assertion = assertion.replaceAll("\\$(\\w+)\\.trim\\(\\)", "string::trim(\\$$1)");
            wasFixed = true;
        }

        // Fix .toLowerCase() -> string::lowercase()
        if (assertion.contains(".toLowerCase()")) {
            assertion = assertion.replaceAll("\\$(\\w+)\\.toLowerCase\\(\\)", "string::lowercase(\\$$1)");
            wasFixed = true;
        }

        // Fix .toUpperCase() -> string::uppercase()
        if (assertion.contains(".toUpperCase()")) {
            assertion = assertion.replaceAll("\\$(\\w+)\\.toUpperCase\\(\\)", "string::uppercase(\\$$1)");
            wasFixed = true;
        }

        // Fix .isEmpty() -> string::len($value) == 0
        if (assertion.contains(".isEmpty()")) {
            assertion = assertion.replaceAll("\\$(\\w+)\\.isEmpty\\(\\)", "string::len(\\$$1) == 0");
            wasFixed = true;
        }

        // Fix .isBlank() -> string::len(string::trim($value)) == 0
        if (assertion.contains(".isBlank()")) {
            assertion = assertion.replaceAll("\\$(\\w+)\\.isBlank\\(\\)", "string::len(string::trim(\\$$1)) == 0");
            wasFixed = true;
        }

        // Fix .startsWith("x") -> string::starts_with($value, "x")
        if (assertion.contains(".startsWith(")) {
            assertion = assertion.replaceAll("\\$(\\w+)\\.startsWith\\(([^)]+)\\)", "string::starts_with(\\$$1, $2)");
            wasFixed = true;
        }

        // Fix .endsWith("x") -> string::ends_with($value, "x")
        if (assertion.contains(".endsWith(")) {
            assertion = assertion.replaceAll("\\$(\\w+)\\.endsWith\\(([^)]+)\\)", "string::ends_with(\\$$1, $2)");
            wasFixed = true;
        }

        // Fix .contains("x") -> string::contains($value, "x")
        if (assertion.contains(".contains(")) {
            assertion = assertion.replaceAll("\\$(\\w+)\\.contains\\(([^)]+)\\)", "string::contains(\\$$1, $2)");
            wasFixed = true;
        }

        // Fix .matches("regex") -> string::is::match($value, "regex") - SurrealDB 2.x
        if (assertion.contains(".matches(")) {
            assertion = assertion.replaceAll("\\$(\\w+)\\.matches\\(([^)]+)\\)", "\\$$1 MATCHES $2");
            wasFixed = true;
        }

        if (wasFixed) {
            log.warn("⚠️ Auto-fixed assertion on {}.{}: '{}' → '{}'",
                tableName, fieldName, original, assertion);
            log.warn("   💡 Please update your @OneirosField annotation to use SurrealDB functions!");
        }

        return assertion;
    }

    /**
     * Check if a SurrealDB type contains 'any' which cannot be wrapped in option<>.
     * SurrealDB does not support option<any>, array<any>, set<any>, etc. because
     * 'any' already accepts null/NONE values.
     *
     * <p>Note: This check is separate from the 'object' type check, which is handled inline.
     * Both 'any' and 'object' are nullable types that should not be wrapped in option<>.
     *
     * <p>This method detects:
     * <ul>
     *   <li>"any" - exact type</li>
     *   <li>"array<any>" - generic with any</li>
     *   <li>"set<any>" - generic with any</li>
     *   <li>"option<any>" - already optional</li>
     *   <li>Any nested combination containing 'any'</li>
     * </ul>
     *
     * @param type The SurrealDB type string
     * @return true if the type is 'any' or contains 'any' as a generic parameter
     */
    private boolean containsAnyType(String type) {
        if (type == null) {
            return false;
        }

        // Normalize and trim
        String normalizedType = type.trim().toLowerCase();

        // Exact match for 'any'
        if (normalizedType.equals("any")) {
            return true;
        }

        // Check for 'any' inside generic parameters: array<any>, set<any>, option<any>, etc.
        // Also handles nested cases like array<option<any>>
        // Use word boundary check to avoid matching 'many' or 'company' etc.
        if (normalizedType.contains("<any>") ||
            normalizedType.contains("<any,") ||
            normalizedType.contains(",any>") ||
            normalizedType.contains(", any>") ||
            normalizedType.contains("<any ") ||
            normalizedType.contains(" any>")) {
            return true;
        }

        return false;
    }

    private String escapeString(String s) {
        return s.replace("'", "\\'");
    }
}
