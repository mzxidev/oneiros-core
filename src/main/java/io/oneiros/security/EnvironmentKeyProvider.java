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
 * Key provider that loads the encryption key from an environment variable.
 *
 * <p>This is more secure than hardcoding keys in configuration files, and is
 * suitable for containerized deployments where secrets are injected as environment
 * variables (e.g., Kubernetes Secrets, Docker secrets, AWS ECS task definitions).
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Default environment variable name: ONEIROS_ENCRYPTION_KEY
 * KeyProvider provider = new EnvironmentKeyProvider();
 *
 * // Custom environment variable name
 * KeyProvider provider = new EnvironmentKeyProvider("MY_APP_SECRET_KEY");
 * }</pre>
 *
 * <h3>Security Best Practices</h3>
 * <ul>
 *   <li>Use Kubernetes Secrets or cloud secret managers to inject the env var</li>
 *   <li>Ensure the environment variable is not logged during startup</li>
 *   <li>Restrict access to the container/process environment</li>
 *   <li>Rotate keys by updating the secret and redeploying</li>
 * </ul>
 *
 * @see KeyProvider
 * @see InMemoryKeyProvider
 * @since 0.4.2
 */
public class EnvironmentKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentKeyProvider.class);

    /**
     * Default environment variable name for the encryption key.
     */
    public static final String DEFAULT_ENV_VAR = "ONEIROS_ENCRYPTION_KEY";

    private final String envVarName;
    private final SecretKey secretKey;
    private final String keyId;

    /**
     * Creates a key provider using the default environment variable.
     *
     * @throws KeyProviderException if the environment variable is not set or invalid
     */
    public EnvironmentKeyProvider() {
        this(DEFAULT_ENV_VAR);
    }

    /**
     * Creates a key provider using a custom environment variable.
     *
     * @param envVarName the name of the environment variable containing the key
     * @throws KeyProviderException if the environment variable is not set or invalid
     */
    public EnvironmentKeyProvider(String envVarName) {
        this.envVarName = envVarName;

        String keyValue = System.getenv(envVarName);
        if (keyValue == null || keyValue.isBlank()) {
            throw new KeyProviderException("ENVIRONMENT", "key_loading",
                    String.format("Environment variable '%s' is not set or empty", envVarName));
        }

        if (keyValue.length() < 14) {
            throw new KeyProviderException("ENVIRONMENT", "key_validation",
                    String.format("Key in '%s' must be at least 14 characters (NIST SP 800-63B)", envVarName));
        }
        if (keyValue.length() < 20) {
            log.warn("⚠️ Passphrase in {} is shorter than 20 characters. Consider using a longer passphrase.", envVarName);
        }

        try {
            this.secretKey = deriveKey(keyValue);
            this.keyId = computeKeyId(secretKey);
            log.info("🔑 EnvironmentKeyProvider initialized from {} (keyId: {}...)",
                    envVarName, keyId.substring(0, 8));
        } catch (NoSuchAlgorithmException e) {
            throw new KeyProviderException("ENVIRONMENT", "key_derivation",
                    "SHA-256 not available", e);
        }
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
        return Optional.of(KeyProviderMetadata.environment(envVarName));
    }

    /**
     * Returns the name of the environment variable used.
     */
    public String getEnvVarName() {
        return envVarName;
    }

    private SecretKey deriveKey(String password) throws NoSuchAlgorithmException {
        try {
            // K1 FIX: Secure randomly generated and persistent salt instead of static string
            byte[] salt = SaltManager.getOrCreateSalt();

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

