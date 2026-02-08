package io.oneiros.security;

import io.oneiros.config.OneirosProperties;
import io.oneiros.core.OneirosConfig;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cryptography service for encryption and password hashing.
 *
 * <p>Supports multiple encryption/hashing algorithms:
 * <ul>
 *   <li><strong>AES_GCM</strong> - Symmetric encryption (reversible)</li>
 *   <li><strong>ARGON2</strong> - Modern password hashing (recommended)</li>
 *   <li><strong>BCRYPT</strong> - Widely-used password hashing</li>
 *   <li><strong>SCRYPT</strong> - Memory-hard password hashing</li>
 *   <li><strong>SHA256/SHA512</strong> - Simple hashing (not for passwords)</li>
 * </ul>
 *
 * <p><strong>Note:</strong> This class uses Spring Security for password encoding
 * but works in non-Spring environments as well. Spring Security Crypto is a
 * standalone library that doesn't require the Spring Framework.
 *
 * @see EncryptionType
 */
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // Bit
    private static final int IV_LENGTH = 12; // Bytes (Standard für GCM)

    // Default strengths for password hashing algorithms
    private static final int DEFAULT_BCRYPT_STRENGTH = 10;
    private static final int DEFAULT_ARGON2_SALT_LENGTH = 16;
    private static final int DEFAULT_ARGON2_HASH_LENGTH = 32;
    private static final int DEFAULT_ARGON2_PARALLELISM = 1;
    private static final int DEFAULT_ARGON2_MEMORY = 65536; // 64 MB
    private static final int DEFAULT_ARGON2_ITERATIONS = 3;
    private static final int DEFAULT_SCRYPT_CPU_COST = 16384;
    private static final int DEFAULT_SCRYPT_MEMORY_COST = 8;
    private static final int DEFAULT_SCRYPT_PARALLELIZATION = 1;
    private static final int DEFAULT_SCRYPT_KEY_LENGTH = 32;
    private static final int DEFAULT_SCRYPT_SALT_LENGTH = 16;

    /**
     * -- GETTER --
     *  Checks if the encryption/security feature is enabled.
     */
    @Getter
    private final boolean enabled;
    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    // Cache for password encoders with different strengths
    private final Map<String, PasswordEncoder> encoderCache = new ConcurrentHashMap<>();

    /**
     * Creates a CryptoService from Spring OneirosProperties (Spring Boot integration).
     *
     * @param properties the Spring configuration properties
     */
    public CryptoService(OneirosProperties properties) {
        this(properties.getSecurity().isEnabled(), properties.getSecurity().getKey());
    }

    /**
     * Creates a CryptoService from framework-agnostic OneirosConfig.
     *
     * @param config the framework-agnostic configuration
     */
    public CryptoService(OneirosConfig config) {
        this(config.getSecurity().isEnabled(), config.getSecurity().getKey());
    }

    /**
     * Creates a CryptoService with explicit parameters.
     *
     * @param enabled whether encryption is enabled
     * @param keyString the encryption key (must be >= 8 characters if enabled)
     */
    public CryptoService(boolean enabled, String keyString) {
        this.enabled = enabled;

        if (enabled) {
            if (keyString == null || keyString.length() < 8) {
                throw new IllegalArgumentException("❌ Oneiros Security is enabled but Key is too short or missing!");
            }
            // Hash the user key with SHA-256 to ensure it's exactly 32 bytes (256 bit)
            this.secretKey = generateKey(keyString);
            log.info("🔒 Oneiros Vault initialized (AES-256-GCM)");
        } else {
            this.secretKey = null;
            log.info("🔓 Oneiros Encryption disabled");
        }
    }

    private SecretKey generateKey(String key) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(key.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Crypto Init Error", e);
        }
    }

    // ==================== Main API ====================

    /**
     * Encrypts or hashes the given value using the specified encryption type.
     *
     * @param plainText the value to encrypt/hash
     * @param type the encryption type to use
     * @param strength the strength/cost factor (-1 for default)
     * @return the encrypted/hashed value
     */
    public String encrypt(String plainText, EncryptionType type, int strength) {
        if (!enabled || plainText == null) return plainText;

        return switch (type) {
            case AES_GCM -> encryptAesGcm(plainText);
            case ARGON2 -> hashArgon2(plainText, strength);
            case BCRYPT -> hashBCrypt(plainText, strength);
            case SCRYPT -> hashSCrypt(plainText, strength);
            case SHA256 -> hashSha256(plainText);
            case SHA512 -> hashSha512(plainText);
        };
    }

    /**
     * Encrypts using AES-GCM (default, for backward compatibility).
     */
    public String encrypt(String plainText) {
        return encrypt(plainText, EncryptionType.AES_GCM, -1);
    }

    /**
     * Decrypts the given value. Only works for AES_GCM encryption.
     * For password hashes (ARGON2, BCRYPT, SCRYPT), use verify() instead.
     *
     * @param encryptedText the encrypted value
     * @param type the encryption type used
     * @return the decrypted value, or the input if not decryptable
     */
    public String decrypt(String encryptedText, EncryptionType type) {
        if (!enabled || encryptedText == null) return encryptedText;

        if (type == EncryptionType.AES_GCM) {
            return decryptAesGcm(encryptedText);
        }

        // One-way hashes cannot be decrypted
        log.warn("Cannot decrypt {} hash - use verify() instead", type);
        return encryptedText;
    }

    /**
     * Decrypts using AES-GCM (default, for backward compatibility).
     */
    public String decrypt(String encryptedText) {
        return decrypt(encryptedText, EncryptionType.AES_GCM);
    }

    /**
     * Verifies a plaintext password against a stored hash.
     * Works for ARGON2, BCRYPT, SCRYPT, SHA256, SHA512.
     *
     * @param plainText the plaintext password to check
     * @param hashedValue the stored hash
     * @param type the hashing algorithm used
     * @param strength the strength used during hashing (-1 for default)
     * @return true if the password matches
     */
    public boolean verify(String plainText, String hashedValue, EncryptionType type, int strength) {
        if (plainText == null || hashedValue == null) return false;

        return switch (type) {
            case AES_GCM -> plainText.equals(decryptAesGcm(hashedValue));
            case ARGON2 -> getArgon2Encoder(strength).matches(plainText, hashedValue);
            case BCRYPT -> getBCryptEncoder(strength).matches(plainText, hashedValue);
            case SCRYPT -> getSCryptEncoder(strength).matches(plainText, hashedValue);
            case SHA256 -> hashSha256(plainText).equals(hashedValue);
            case SHA512 -> hashSha512(plainText).equals(hashedValue);
        };
    }

    /**
     * Verifies a password using BCrypt (convenience method).
     */
    public boolean verifyBCrypt(String plainText, String hashedValue) {
        return verify(plainText, hashedValue, EncryptionType.BCRYPT, -1);
    }

    /**
     * Verifies a password using Argon2 (convenience method).
     */
    public boolean verifyArgon2(String plainText, String hashedValue) {
        return verify(plainText, hashedValue, EncryptionType.ARGON2, -1);
    }

    // ==================== AES-GCM ====================

    private String encryptAesGcm(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            throw new RuntimeException("AES-GCM encryption failed", e);
        }
    }

    private String decryptAesGcm(String encryptedText) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);

            int cipherTextLength = decoded.length - IV_LENGTH;
            byte[] cipherText = new byte[cipherTextLength];
            System.arraycopy(decoded, IV_LENGTH, cipherText, 0, cipherTextLength);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to decrypt AES-GCM data: {}", e.getMessage());
            return encryptedText;
        }
    }

    // ==================== Argon2 ====================

    private Argon2PasswordEncoder getArgon2Encoder(int strength) {
        int memory = strength > 0 ? strength : DEFAULT_ARGON2_MEMORY;
        String key = "argon2:" + memory;

        return (Argon2PasswordEncoder) encoderCache.computeIfAbsent(key, k ->
            new Argon2PasswordEncoder(
                DEFAULT_ARGON2_SALT_LENGTH,
                DEFAULT_ARGON2_HASH_LENGTH,
                DEFAULT_ARGON2_PARALLELISM,
                memory,
                DEFAULT_ARGON2_ITERATIONS
            )
        );
    }

    private String hashArgon2(String plainText, int strength) {
        return getArgon2Encoder(strength).encode(plainText);
    }

    // ==================== BCrypt ====================

    private BCryptPasswordEncoder getBCryptEncoder(int strength) {
        int cost = (strength > 0 && strength >= 4 && strength <= 31) ? strength : DEFAULT_BCRYPT_STRENGTH;
        String key = "bcrypt:" + cost;

        return (BCryptPasswordEncoder) encoderCache.computeIfAbsent(key, k ->
            new BCryptPasswordEncoder(cost)
        );
    }

    private String hashBCrypt(String plainText, int strength) {
        return getBCryptEncoder(strength).encode(plainText);
    }

    // ==================== SCrypt ====================

    private SCryptPasswordEncoder getSCryptEncoder(int strength) {
        int cpuCost = strength > 0 ? strength : DEFAULT_SCRYPT_CPU_COST;
        String key = "scrypt:" + cpuCost;

        return (SCryptPasswordEncoder) encoderCache.computeIfAbsent(key, k ->
            new SCryptPasswordEncoder(
                cpuCost,
                DEFAULT_SCRYPT_MEMORY_COST,
                DEFAULT_SCRYPT_PARALLELIZATION,
                DEFAULT_SCRYPT_KEY_LENGTH,
                DEFAULT_SCRYPT_SALT_LENGTH
            )
        );
    }

    private String hashSCrypt(String plainText, int strength) {
        return getSCryptEncoder(strength).encode(plainText);
    }

    // ==================== SHA Hashing ====================

    private String hashSha256(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }

    private String hashSha512(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-512 hashing failed", e);
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Checks if the given hash looks like a BCrypt hash.
     */
    public boolean isBCryptHash(String hash) {
        return hash != null && hash.startsWith("$2");
    }

    /**
     * Checks if the given hash looks like an Argon2 hash.
     */
    public boolean isArgon2Hash(String hash) {
        return hash != null && hash.startsWith("$argon2");
    }

    /**
     * Checks if the given hash looks like an SCrypt hash.
     */
    public boolean isSCryptHash(String hash) {
        return hash != null && hash.startsWith("$e0801$");
    }
}

