package io.oneiros.security;

import javax.crypto.SecretKey;
import java.util.Optional;

/**
 * Interface for cryptographic key providers.
 *
 * <p>This abstraction allows different key management strategies:
 * <ul>
 *   <li><strong>InMemoryKeyProvider</strong> - Default, stores key in application memory</li>
 *   <li><strong>EnvironmentKeyProvider</strong> - Loads key from environment variables</li>
 *   <li><strong>KMS Integration</strong> - AWS KMS, Azure Key Vault, GCP Cloud KMS</li>
 *   <li><strong>HSM Integration</strong> - Hardware Security Modules (PKCS#11)</li>
 *   <li><strong>Vault Integration</strong> - HashiCorp Vault, CyberArk, etc.</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong> The default {@link InMemoryKeyProvider} stores
 * the encryption key in application memory. For high-security environments, consider
 * implementing a custom KeyProvider that integrates with your organization's KMS or HSM.
 *
 * <h3>Example: Custom AWS KMS Integration</h3>
 * <pre>{@code
 * public class AwsKmsKeyProvider implements KeyProvider {
 *     private final KmsClient kmsClient;
 *     private final String keyId;
 *
 *     @Override
 *     public SecretKey getSecretKey() {
 *         // Use KMS to generate/unwrap data key
 *         var response = kmsClient.generateDataKey(req -> req.keyId(keyId));
 *         return new SecretKeySpec(response.plaintext().asByteArray(), "AES");
 *     }
 *
 *     @Override
 *     public void rotateKey() {
 *         // Trigger key rotation in KMS
 *         kmsClient.enableKeyRotation(req -> req.keyId(keyId));
 *     }
 * }
 * }</pre>
 *
 * @see InMemoryKeyProvider
 * @see EnvironmentKeyProvider
 * @see CryptoService
 * @since 0.4.2
 */
public interface KeyProvider {

    /**
     * Returns the secret key for encryption/decryption operations.
     *
     * <p>Implementations should handle key caching appropriately:
     * <ul>
     *   <li>In-memory providers return cached key</li>
     *   <li>KMS providers may cache the data key with TTL</li>
     *   <li>HSM providers typically retrieve on each call</li>
     * </ul>
     *
     * @return the secret key, never null when encryption is enabled
     * @throws KeyProviderException if the key cannot be retrieved
     */
    SecretKey getSecretKey();

    /**
     * Returns the key identifier for auditing and key rotation purposes.
     *
     * <p>This can be:
     * <ul>
     *   <li>A hash of the key material (for in-memory)</li>
     *   <li>KMS key ARN or ID</li>
     *   <li>HSM key label</li>
     *   <li>Vault secret path</li>
     * </ul>
     *
     * @return unique identifier for the current key
     */
    String getKeyId();

    /**
     * Returns the key version for rotation tracking.
     *
     * @return current key version, or "1" if versioning is not supported
     */
    default String getKeyVersion() {
        return "1";
    }

    /**
     * Rotates to a new key version if supported.
     *
     * <p>Not all providers support key rotation:
     * <ul>
     *   <li>In-memory providers throw UnsupportedOperationException</li>
     *   <li>KMS providers can trigger rotation</li>
     *   <li>Some HSMs support automatic rotation</li>
     * </ul>
     *
     * @throws UnsupportedOperationException if rotation is not supported
     * @throws KeyProviderException if rotation fails
     */
    default void rotateKey() {
        throw new UnsupportedOperationException("Key rotation not supported by this provider");
    }

    /**
     * Checks if this provider supports key rotation.
     *
     * @return true if rotateKey() is supported
     */
    default boolean supportsRotation() {
        return false;
    }

    /**
     * Returns metadata about the key provider for auditing.
     *
     * @return optional metadata (provider type, region, etc.)
     */
    default Optional<KeyProviderMetadata> getMetadata() {
        return Optional.empty();
    }

    /**
     * Validates that the key provider is properly configured and operational.
     *
     * @throws KeyProviderException if validation fails
     */
    default void validate() {
        // Default implementation just tries to get the key
        getSecretKey();
    }

    /**
     * Cleans up resources when the provider is no longer needed.
     * Called during application shutdown.
     */
    default void close() {
        // Default: no cleanup needed
    }
}

