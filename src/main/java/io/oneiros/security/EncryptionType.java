package io.oneiros.security;

/**
 * Supported encryption and hashing algorithms for @OneirosEncrypted fields.
 */
public enum EncryptionType {

    /**
     * AES-256-GCM encryption (default).
     * Reversible encryption - use for data that needs to be decrypted.
     * Good for: API keys, tokens, sensitive data that needs to be read back.
     */
    AES_GCM,

    /**
     * Argon2id password hashing (recommended for passwords).
     * One-way hashing - cannot be reversed, only verified.
     * Best for: User passwords, PINs, secrets.
     * Uses Argon2id variant which is resistant to GPU and side-channel attacks.
     */
    ARGON2,

    /**
     * BCrypt password hashing.
     * One-way hashing - cannot be reversed, only verified.
     * Good for: User passwords (widely used, well-tested).
     */
    BCRYPT,

    /**
     * SCrypt password hashing.
     * One-way hashing - cannot be reversed, only verified.
     * Good for: User passwords (memory-hard, resistant to hardware attacks).
     */
    SCRYPT,

    /**
     * SHA-256 hashing.
     * One-way hashing - fast but NOT recommended for passwords.
     * Use only for: Checksums, non-sensitive hashing, data integrity.
     */
    SHA256,

    /**
     * SHA-512 hashing.
     * One-way hashing - fast but NOT recommended for passwords.
     * Use only for: Checksums, non-sensitive hashing, data integrity.
     */
    SHA512
}
