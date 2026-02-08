package io.oneiros.annotation;

import io.oneiros.security.EncryptionType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks fields that should be encrypted or hashed before saving to the database.
 *
 * <p>Usage examples:</p>
 * <pre>
 * // AES-256-GCM encryption (default, reversible)
 * {@literal @}OneirosEncrypted
 * private String apiKey;
 *
 * // Argon2 password hashing (recommended for passwords)
 * {@literal @}OneirosEncrypted(type = EncryptionType.ARGON2)
 * private String password;
 *
 * // BCrypt password hashing
 * {@literal @}OneirosEncrypted(type = EncryptionType.BCRYPT)
 * private String password;
 *
 * // BCrypt with custom strength (4-31, default 10)
 * {@literal @}OneirosEncrypted(type = EncryptionType.BCRYPT, strength = 12)
 * private String password;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneirosEncrypted {

    /**
     * The encryption/hashing algorithm to use.
     * Default is AES_GCM (reversible encryption).
     *
     * @return the encryption type
     */
    EncryptionType type() default EncryptionType.AES_GCM;

    /**
     * Strength/cost factor for password hashing algorithms.
     * - BCrypt: 4-31 (default 10, higher = slower but more secure)
     * - Argon2: memory cost in KB (default 65536 = 64MB)
     * - SCrypt: CPU/memory cost parameter N (default 16384)
     *
     * Ignored for AES_GCM, SHA256, SHA512.
     *
     * @return the strength/cost factor
     */
    int strength() default -1; // -1 means use algorithm default

    /**
     * Whether to automatically verify passwords during entity load.
     * Only applicable for one-way hashing algorithms (ARGON2, BCRYPT, SCRYPT).
     *
     * When false, the hashed value is returned as-is.
     * When true, you can use CryptoService.verify() to check passwords.
     *
     * @return whether verification is enabled
     */
    boolean verifiable() default true;
}