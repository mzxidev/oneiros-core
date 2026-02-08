package io.oneiros.security;

/**
 * Supported hash algorithms for password hashing.
 */
public enum HashAlgorithm {
    /**
     * Argon2id - Winner of Password Hashing Competition, recommended for new applications.
     * Memory-hard, resistant to GPU/ASIC attacks.
     */
    ARGON2,

    /**
     * BCrypt - Industry standard, well-tested, good for most use cases.
     * Adaptive cost factor allows tuning computational cost.
     */
    BCRYPT,

    /**
     * SCrypt - Memory-hard algorithm, good alternative to Argon2.
     * Resistant to hardware brute-force attacks.
     */
    SCRYPT
}

