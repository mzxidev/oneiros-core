package io.oneiros.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

/**
 * Default key provider that stores the encryption key in application memory.
 *
 * <p>This is the standard key provider suitable for most applications. The key
 * is derived from a user-provided password/passphrase using SHA-256 hashing
 * to ensure exactly 256 bits (32 bytes) for AES-256.
 *
 * <h3>Security Considerations</h3>
 * <ul>
 *   <li>Key resides in JVM heap memory during application runtime</li>
 *   <li>Key may be visible in heap dumps</li>
 *   <li>Key may persist in swap space if memory is swapped</li>
 *   <li>GC may leave copies of key material in freed memory</li>
 * </ul>
 *
 * <p>For high-security environments requiring HSM or cloud KMS integration,
 * implement a custom {@link KeyProvider}.
 *
 * @see KeyProvider
 * @see EnvironmentKeyProvider
 * @since 0.4.2
 */
public class InMemoryKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryKeyProvider.class);

    private final SecretKey secretKey;
    private final String keyId;

    /**
     * Creates an in-memory key provider from a password/passphrase.
     *
     * @param keyString the password or passphrase (minimum 8 characters)
     * @throws KeyProviderException if key derivation fails
     */
    public InMemoryKeyProvider(String keyString) {
        if (keyString == null || keyString.length() < 8) {
            throw new KeyProviderException("IN_MEMORY", "key_derivation",
                    "Key must be at least 8 characters");
        }

        try {
            this.secretKey = deriveKey(keyString);
            this.keyId = computeKeyId(secretKey);
            log.debug("🔑 InMemoryKeyProvider initialized (keyId: {}...)", keyId.substring(0, 8));
        } catch (NoSuchAlgorithmException e) {
            throw new KeyProviderException("IN_MEMORY", "key_derivation",
                    "SHA-256 not available", e);
        }
    }

    /**
     * Creates an in-memory key provider with an existing SecretKey.
     *
     * @param secretKey the secret key (must be AES-256)
     * @throws KeyProviderException if key is invalid
     */
    public InMemoryKeyProvider(SecretKey secretKey) {
        if (secretKey == null) {
            throw new KeyProviderException("IN_MEMORY", "initialization",
                    "SecretKey cannot be null");
        }
        if (secretKey.getEncoded().length != 32) {
            throw new KeyProviderException("IN_MEMORY", "initialization",
                    "Key must be exactly 256 bits (32 bytes) for AES-256");
        }

        this.secretKey = secretKey;
        this.keyId = computeKeyId(secretKey);
        log.debug("🔑 InMemoryKeyProvider initialized with existing key (keyId: {}...)", keyId.substring(0, 8));
    }

    @Override
    public SecretKey getSecretKey() {
        return secretKey;
    }

    @Override
    public String getKeyId() {
        return keyId;
    }

    @Override
    public Optional<KeyProviderMetadata> getMetadata() {
        return Optional.of(KeyProviderMetadata.inMemory());
    }

    /**
     * Derives an AES-256 key from a password using PBKDF2 (OWASP recommended).
     */
    private SecretKey deriveKey(String password) throws NoSuchAlgorithmException {
        try {
            // Use PBKDF2 for secure key derivation (OWASP recommended)
            // Salt is derived from a constant (not ideal for rotation, but better than no salt)
            // For production: Use a properly stored/rotated salt
            byte[] salt = "oneiros-kdf-salt-v1".getBytes(StandardCharsets.UTF_8);

            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                password.toCharArray(),
                salt,
                310000, // OWASP recommendation (2023): 310,000 iterations for PBKDF2-SHA256
                256     // 256-bit key for AES-256
            );

            javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            spec.clearPassword(); // Clear sensitive data

            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new NoSuchAlgorithmException("PBKDF2 key derivation failed", e);
        }
    }

    /**
     * Computes a non-reversible identifier for the key (first 16 chars of SHA-256 hash).
     */
    private String computeKeyId(SecretKey key) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(key.getEncoded());
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}

