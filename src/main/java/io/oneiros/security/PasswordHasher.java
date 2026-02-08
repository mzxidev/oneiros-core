package io.oneiros.security;

import lombok.Getter;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

/**
 * Password hasher using Spring Security's password encoding utilities.
 *
 * <p>Supports multiple hashing algorithms:
 * <ul>
 *   <li><b>Argon2id</b> - Recommended for new applications (winner of Password Hashing Competition)</li>
 *   <li><b>BCrypt</b> - Industry standard, well-tested</li>
 *   <li><b>SCrypt</b> - Memory-hard alternative</li>
 * </ul>
 *
 * <p><b>Important:</b> Password hashes are NOT reversible. Use {@link CryptoService}
 * with AES encryption for reversible encryption.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * PasswordHasher hasher = new PasswordHasher(HashAlgorithm.ARGON2);
 *
 * // Hash password
 * String hashed = hasher.hash("myPassword");
 *
 * // Verify password
 * boolean valid = hasher.verify("myPassword", hashed);
 * }</pre>
 *
 * @see EncryptionType
 * @see CryptoService
 */
public class PasswordHasher {

    private final PasswordEncoder encoder;
    /**
     * -- GETTER --
     *  Gets the algorithm used by this hasher.
     *
     * @return The hash algorithm
     */
    @Getter
    private final EncryptionType algorithm;

    /**
     * Creates a password hasher with the specified algorithm.
     *
     * @param algorithm The hashing algorithm to use
     */
    public PasswordHasher(EncryptionType algorithm) {
        this.algorithm = algorithm;
        this.encoder = createEncoder(algorithm);
    }

    /**
     * Hashes a plaintext password.
     *
     * @param plaintext The plaintext password
     * @return The hashed password (includes salt and algorithm metadata)
     */
    public String hash(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return encoder.encode(plaintext);
    }

    /**
     * Hashes a password with a specific algorithm (ignores instance algorithm).
     *
     * @param plaintext The plaintext password
     * @param algorithm The algorithm to use for this specific hash
     * @return The hashed password
     */
    public String hash(String plaintext, EncryptionType algorithm) {
        PasswordEncoder specificEncoder = createEncoder(algorithm);
        return specificEncoder.encode(plaintext);
    }

    /**
     * Verifies a plaintext password against a hash.
     *
     * @param plaintext The plaintext password to verify
     * @param hashed The hashed password to compare against
     * @return true if the password matches, false otherwise
     */
    public boolean verify(String plaintext, String hashed) {
        if (plaintext == null || hashed == null) {
            return false;
        }
        return encoder.matches(plaintext, hashed);
    }

    /**
     * Creates the appropriate encoder for the specified algorithm.
     */
    private static PasswordEncoder createEncoder(EncryptionType algorithm) {
        return switch (algorithm) {
            case ARGON2 -> Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
            case BCRYPT -> new BCryptPasswordEncoder();
            case SCRYPT -> SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8();
            case AES_GCM -> throw new IllegalArgumentException("AES_GCM is not a password hashing algorithm. Use CryptoService for encryption.");
            case SHA256, SHA512 -> throw new UnsupportedOperationException("SHA hashing not supported in PasswordHasher. Use ARGON2, BCRYPT, or SCRYPT.");
        };
    }
}

