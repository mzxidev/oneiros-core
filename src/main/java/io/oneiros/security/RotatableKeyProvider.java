package io.oneiros.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Key provider that supports key rotation with backward compatibility.
 *
 * <p>This provider maintains multiple key versions:
 * <ul>
 *   <li><strong>Current Key:</strong> Used for all new encryption operations</li>
 *   <li><strong>Previous Keys:</strong> Kept for decrypting data encrypted with old keys</li>
 * </ul>
 *
 * <h3>Key Rotation Process</h3>
 * <ol>
 *   <li>Generate new key version</li>
 *   <li>Set as current encryption key</li>
 *   <li>Keep old keys for decryption (configurable retention)</li>
 *   <li>Re-encrypt data gradually (background process recommended)</li>
 * </ol>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * RotatableKeyProvider provider = new RotatableKeyProvider("initial-key");
 *
 * // Encrypt with current key (version 1)
 * String encrypted = cryptoService.encrypt("sensitive data");
 *
 * // Rotate to new key
 * provider.rotateKey("new-key");
 *
 * // Old data can still be decrypted (version 1)
 * String decrypted = cryptoService.decrypt(encrypted);
 *
 * // New encryptions use version 2
 * String newEncrypted = cryptoService.encrypt("new data");
 * }</pre>
 *
 * @see KeyProvider
 * @since 0.4.2
 */
public class RotatableKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RotatableKeyProvider.class);

    /**
     * Immutable holder for key + version (thread-safe atomic updates).
     */
    private static class VersionedKey {
        final SecretKey key;
        final int version;

        VersionedKey(SecretKey key, int version) {
            this.key = key;
            this.version = version;
        }
    }

    // SECURITY FIX: Use AtomicReference to update key+version atomically
    private final AtomicReference<VersionedKey> currentVersionedKey;
    private final Map<Integer, SecretKey> keyHistory = new ConcurrentHashMap<>();
    private final AtomicInteger versionCounter = new AtomicInteger(1);
    private final int maxKeyHistorySize;

    /**
     * Creates a rotatable key provider with default history size (3 previous keys).
     *
     * @param initialKey the initial encryption key
     */
    public RotatableKeyProvider(String initialKey) {
        this(initialKey, 3);
    }

    /**
     * Creates a rotatable key provider with custom history size.
     *
     * @param initialKey the initial encryption key
     * @param maxKeyHistorySize maximum number of old keys to retain (recommended: 3-5)
     */
    public RotatableKeyProvider(String initialKey, int maxKeyHistorySize) {
        if (initialKey == null || initialKey.length() < 8) {
            throw new KeyProviderException("ROTATABLE", "initialization",
                    "Initial key must be at least 8 characters");
        }
        if (maxKeyHistorySize < 1) {
            throw new KeyProviderException("ROTATABLE", "initialization",
                    "maxKeyHistorySize must be at least 1");
        }

        this.maxKeyHistorySize = maxKeyHistorySize;

        try {
            SecretKey initialSecretKey = deriveKey(initialKey);
            this.currentVersionedKey = new AtomicReference<>(new VersionedKey(initialSecretKey, 1));
            this.keyHistory.put(1, initialSecretKey);

            log.info("🔑 RotatableKeyProvider initialized (version: 1, maxHistory: {})", maxKeyHistorySize);
        } catch (NoSuchAlgorithmException e) {
            throw new KeyProviderException("ROTATABLE", "initialization",
                    "Failed to derive initial key", e);
        }
    }

    @Override
    public SecretKey getSecretKey() {
        return currentVersionedKey.get().key;
    }

    @Override
    public String getKeyId() {
        return "rotatable-v" + currentVersionedKey.get().version;
    }

    @Override
    public String getKeyVersion() {
        return String.valueOf(currentVersionedKey.get().version);
    }

    @Override
    public boolean supportsRotation() {
        return true;
    }

    /**
     * Rotates to a new key version.
     *
     * <p>The old key is retained for decryption according to the history size policy.
     * After rotation, all new encryption operations will use the new key.
     *
     * <p><strong>Important:</strong> After rotating keys, you should re-encrypt
     * existing sensitive data in the background to use the new key.
     *
     * @throws KeyProviderException if rotation fails
     */
    @Override
    public void rotateKey() {
        throw new UnsupportedOperationException(
                "Use rotateKey(String newKey) to provide the new key material");
    }

    /**
     * Rotates to a new key version with provided key material.
     *
     * @param newKey the new key material (minimum 8 characters)
     * @throws KeyProviderException if rotation fails
     */
    public synchronized void rotateKey(String newKey) {
        if (newKey == null || newKey.length() < 8) {
            throw new KeyProviderException("ROTATABLE", "rotation",
                    "New key must be at least 8 characters");
        }

        try {
            VersionedKey current = currentVersionedKey.get();
            int oldVersion = current.version;
            int newVersion = versionCounter.incrementAndGet();

            SecretKey newSecretKey = deriveKey(newKey);

            // Add new key to history
            keyHistory.put(newVersion, newSecretKey);

            // SECURITY FIX: Atomically update key + version together
            currentVersionedKey.set(new VersionedKey(newSecretKey, newVersion));

            // Cleanup old keys if history is too large
            if (keyHistory.size() > maxKeyHistorySize) {
                int oldestVersion = newVersion - maxKeyHistorySize;
                keyHistory.entrySet().removeIf(entry -> entry.getKey() < oldestVersion);
                log.debug("🗑️ Removed old key versions (keeping last {})", maxKeyHistorySize);
            }

            log.info("🔄 Key rotated: v{} -> v{} (history size: {})",
                    oldVersion, newVersion, keyHistory.size());

        } catch (NoSuchAlgorithmException e) {
            throw new KeyProviderException("ROTATABLE", "rotation",
                    "Failed to derive new key", e);
        }
    }

    /**
     * Retrieves a specific key version for decryption.
     *
     * <p>This method allows decrypting data that was encrypted with an older key version.
     *
     * @param version the key version to retrieve
     * @return the secret key for that version, or null if not available
     */
    public SecretKey getKeyByVersion(int version) {
        SecretKey key = keyHistory.get(version);
        if (key == null) {
            VersionedKey current = currentVersionedKey.get();
            log.warn("⚠️ Requested key version {} not available (current: {}, available: {})",
                    version, current.version, keyHistory.keySet());
        }
        return key;
    }

    /**
     * Returns the number of key versions currently stored.
     */
    public int getKeyHistorySize() {
        return keyHistory.size();
    }

    /**
     * Returns all available key versions.
     */
    public java.util.Set<Integer> getAvailableVersions() {
        return java.util.Collections.unmodifiableSet(keyHistory.keySet());
    }

    @Override
    public Optional<KeyProviderMetadata> getMetadata() {
        VersionedKey current = currentVersionedKey.get();
        return Optional.of(KeyProviderMetadata.builder()
                .providerType("ROTATABLE")
                .providerClass(RotatableKeyProvider.class)
                .region("local")
                .keyCreatedAt(java.time.Instant.now())
                .additionalInfo(Map.of(
                        "currentVersion", String.valueOf(current.version),
                        "historySize", String.valueOf(keyHistory.size()),
                        "maxHistorySize", String.valueOf(maxKeyHistorySize),
                        "availableVersions", keyHistory.keySet().toString()
                ))
                .build());
    }

    /**
     * Derives an AES-256 key from a password using PBKDF2 (OWASP recommended).
     */
    private SecretKey deriveKey(String password) throws NoSuchAlgorithmException {
        try {
            // Use PBKDF2 for secure key derivation
            byte[] salt = "oneiros-kdf-salt-v1".getBytes(StandardCharsets.UTF_8);

            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    310000, // OWASP recommendation: 310,000 iterations
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

    @Override
    public void close() {
        // Clear all keys from memory
        keyHistory.clear();
        currentVersionedKey.set(null);
        log.debug("🧹 RotatableKeyProvider cleared all keys from memory");
    }
}
