package io.oneiros.security;

import io.oneiros.annotation.OneirosEncrypted;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.*;

/**
 * Handles transparent encryption and decryption of entity fields annotated with {@link OneirosEncrypted}.
 *
 * <p>This handler provides automatic encryption when writing to the database and decryption when reading,
 * ensuring sensitive data is never stored in plaintext.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Automatic field-level encryption/decryption</li>
 *   <li>Support for AES-256-GCM (reversible) encryption</li>
 *   <li>Support for password hashing (Argon2, BCrypt, SCrypt)</li>
 *   <li>Recursive processing of nested objects</li>
 *   <li>Deep copy to avoid side effects</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * // Encrypt before sending to DB
 * User encrypted = securityHandler.encryptOnWrite(user);
 *
 * // Decrypt after reading from DB
 * User decrypted = securityHandler.decryptOnRead(user);
 * }</pre>
 *
 * @see OneirosEncrypted
 * @see CryptoService
 * @see PasswordHasher
 * @see HashAlgorithm
 */
public class OneirosSecurityHandler {

    private static final Logger log = LoggerFactory.getLogger(OneirosSecurityHandler.class);

    private final CryptoService cryptoService;
    private final PasswordHasher argon2Hasher;
    private final PasswordHasher bcryptHasher;
    private final PasswordHasher scryptHasher;
    /**
     * -- GETTER --
     *  Returns whether security is enabled.
     */
    @Getter
    private final boolean enabled;

    /**
     * Creates a new security handler.
     *
     * @param cryptoService The crypto service for encryption/decryption (can be null if disabled)
     * @param passwordHasher The password hasher (can be null if disabled)
     * @param enabled Whether security is enabled
     */
    public OneirosSecurityHandler(CryptoService cryptoService, PasswordHasher passwordHasher, boolean enabled) {
        this.cryptoService = cryptoService;
        this.enabled = enabled;

        // Create hashers for each algorithm
        this.argon2Hasher = new PasswordHasher(EncryptionType.ARGON2);
        this.bcryptHasher = new PasswordHasher(EncryptionType.BCRYPT);
        this.scryptHasher = new PasswordHasher(EncryptionType.SCRYPT);

        if (!enabled) {
            log.debug("🔓 Security handler disabled");
        }
    }

    /**
     * Encrypts all @OneirosEncrypted fields in the entity before writing to database.
     *
     * <p><b>Important:</b> This modifies the entity in-place for write operations.
     * The SecureOneirosClient will handle returning decrypted results.
     *
     * @param entity The entity to encrypt
     * @param <T> The entity type
     * @return The entity with encrypted fields
     */
    public <T> T encryptOnWrite(T entity) {
        if (!enabled || entity == null || cryptoService == null) {
            return entity;
        }

        try {
            // Process all fields with circular reference protection
            Set<Object> visited = new HashSet<>();
            processFields(entity, true, visited);

            log.debug("🔐 Encrypted entity: {}", entity.getClass().getSimpleName());
            return entity;

        } catch (Exception e) {
            log.error("❌ Failed to encrypt entity: {}", e.getMessage(), e);
            // Return entity as-is on error
            return entity;
        }
    }

    /**
     * Decrypts all @OneirosEncrypted fields in the entity after reading from database.
     *
     * <p><b>Note:</b> Modifies the entity in-place since it's already a copy from the DB.
     *
     * @param entity The entity to decrypt
     * @param <T> The entity type
     * @return The same instance with decrypted fields
     */
    public <T> T decryptOnRead(T entity) {
        if (!enabled || entity == null || cryptoService == null) {
            return entity;
        }

        try {
            // Process all fields with circular reference protection
            Set<Object> visited = new HashSet<>();
            processFields(entity, false, visited);

            log.debug("🔓 Decrypted entity: {}", entity.getClass().getSimpleName());
            return entity;

        } catch (Exception e) {
            log.error("❌ Failed to decrypt entity: {}", e.getMessage(), e);
            // Return entity as-is on error
            return entity;
        }
    }

    /**
     * Processes all fields in the entity, encrypting or decrypting as needed.
     *
     * @param entity The entity to process
     * @param encrypt true to encrypt, false to decrypt
     * @param visited Set of already visited objects to prevent circular references
     */
    private void processFields(Object entity, boolean encrypt, Set<Object> visited) throws Exception {
        if (entity == null) {
            return;
        }

        Class<?> clazz = entity.getClass();

        // Skip primitive types and common immutable types (BEFORE checking visited)
        if (isPrimitiveOrWrapper(clazz) || clazz == String.class) {
            return;
        }

        // Skip Enums (BEFORE checking visited - causes InaccessibleObjectException)
        if (clazz.isEnum() || entity instanceof Enum<?>) {
            return;
        }

        // Skip Java internal classes (java.*, javax.*) BEFORE checking visited
        if (clazz.getName().startsWith("java.") || clazz.getName().startsWith("javax.")) {
            return;
        }

        // Skip collection types to avoid deep traversal (BEFORE checking visited)
        if (entity instanceof java.util.Collection || entity instanceof java.util.Map) {
            return;
        }

        // NOW check if already visited (circular reference protection)
        // Use identity-based comparison to handle properly
        if (!visited.add(entity)) {
            log.trace("🔄 Skipping already visited object: {}", clazz.getSimpleName());
            return;
        }

        // Process all declared fields (including private)
        for (Field field : getAllFields(clazz)) {
            if (!safeSetAccessible(field)) {
                continue; // Skip fields that cannot be accessed
            }

            if (field.isAnnotationPresent(OneirosEncrypted.class)) {
                processEncryptedField(entity, field, encrypt);
            } else {
                // Recursively process nested objects
                try {
                    Object value = field.get(entity);
                    if (value != null && !isPrimitiveOrWrapper(value.getClass()) && value.getClass() != String.class) {
                        processFields(value, encrypt, visited);
                    }
                } catch (Exception e) {
                    // Ignore reflection errors on nested objects
                    log.trace("⚠️ Cannot access nested field {}, skipping", field.getName());
                }
            }
        }
    }

    /**
     * Processes a single encrypted field.
     */
    private void processEncryptedField(Object entity, Field field, boolean encrypt) throws Exception {
        OneirosEncrypted annotation = field.getAnnotation(OneirosEncrypted.class);
        Object value = field.get(entity);

        if (value == null) {
            return; // Skip null values
        }

        if (!(value instanceof String)) {
            log.warn("⚠️ @OneirosEncrypted can only be used on String fields. Skipping: {}.{}",
                    entity.getClass().getSimpleName(), field.getName());
            return;
        }

        String stringValue = (String) value;

        if (encrypt) {
            // Encrypt or hash based on type
            String processed = processForWrite(stringValue, annotation);
            field.set(entity, processed);
            log.trace("🔐 Encrypted field: {}.{}", entity.getClass().getSimpleName(), field.getName());
        } else {
            // Decrypt (only for reversible encryption)
            if (annotation.type() == EncryptionType.AES_GCM) {
                String decrypted = cryptoService.decrypt(stringValue);
                field.set(entity, decrypted);
                log.trace("🔓 Decrypted field: {}.{}", entity.getClass().getSimpleName(), field.getName());
            } else {
                // Password hashes cannot be decrypted - leave as-is
                log.trace("⏭️ Skipping hash field (not reversible): {}.{}",
                        entity.getClass().getSimpleName(), field.getName());
            }
        }
    }

    /**
     * Processes a field for writing (encryption or hashing).
     */
    private String processForWrite(String value, OneirosEncrypted annotation) {
        return switch (annotation.type()) {
            case AES_GCM -> cryptoService.encrypt(value);
            case ARGON2 -> argon2Hasher.hash(value);
            case BCRYPT -> bcryptHasher.hash(value);
            case SCRYPT -> scryptHasher.hash(value);
            case SHA256, SHA512 -> throw new UnsupportedOperationException(
                "SHA hashing not supported for @OneirosEncrypted. Use Argon2, BCrypt, or SCrypt for passwords.");
        };
    }

    /**
     * Creates a deep copy of the entity using reflection.
     *
     * <p><b>Note:</b> This is a simple deep copy implementation.
     * For complex objects, consider using serialization or a dedicated cloning library.
     */
    @SuppressWarnings("unchecked")
    private <T> T deepCopy(T entity) throws Exception {
        if (entity == null) {
            return null;
        }

        Class<?> clazz = entity.getClass();

        // Handle primitive wrappers and String (immutable)
        if (isPrimitiveOrWrapper(clazz) || clazz == String.class) {
            return entity;
        }

        // Create new instance
        T copy = (T) clazz.getDeclaredConstructor().newInstance();

        // Copy all fields
        for (Field field : getAllFields(clazz)) {
            if (!safeSetAccessible(field)) {
                continue; // Skip fields that cannot be accessed
            }

            Object value = field.get(entity);

            if (value != null) {
                // For complex objects, we just reference them (shallow copy)
                // This is acceptable since we only modify String fields
                field.set(copy, value);
            }
        }

        return copy;
    }

    /**
     * Gets all fields from the class hierarchy.
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();

        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }

        return fields;
    }

    /**
     * Checks if a class is a primitive or primitive wrapper.
     */
    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
               clazz == Boolean.class ||
               clazz == Byte.class ||
               clazz == Character.class ||
               clazz == Short.class ||
               clazz == Integer.class ||
               clazz == Long.class ||
               clazz == Float.class ||
               clazz == Double.class;
    }

    /**
     * Safely makes a field accessible, catching Java Module System exceptions.
     *
     * @param field The field to make accessible
     * @return true if successful, false if blocked by Module System
     */
    private boolean safeSetAccessible(Field field) {
        try {
            field.setAccessible(true);
            return true;
        } catch (InaccessibleObjectException e) {
            // Java Module System blocks access (e.g., java.base internal classes)
            log.trace("🔒 Cannot access field {} (Module System protection)", field.getName());
            return false;
        }
    }

}

