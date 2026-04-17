package io.oneiros.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Manages the generation and persistence of the PBKDF2 salt.
 * Ensures the salt is randomly generated to prevent rainbow table attacks,
 * but persisted across restarts so the AES key remains consistent.
 */
public class SaltManager {

    private static final Logger log = LoggerFactory.getLogger(SaltManager.class);
    private static final String SALT_FILE = ".oneiros-salt";
    private static final int SALT_LENGTH = 16;
    private static final byte[] FALLBACK_SALT = "oneiros-kdf-salt-v1".getBytes();

    /**
     * Gets the persisted salt or generates a new one.
     */
    public static byte[] getOrCreateSalt() {
        File saltFile = new File(SALT_FILE);

        if (saltFile.exists()) {
            try {
                String base64Salt = Files.readString(saltFile.toPath()).trim();
                return Base64.getDecoder().decode(base64Salt);
            } catch (Exception e) {
                log.error("❌ Failed to read {}, using fallback salt. WARNING: Data encrypted with different salts cannot be decrypted!", SALT_FILE, e);
                return FALLBACK_SALT; /* Safe fallback */
            }
        }

        // Generate new random salt
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        String base64Salt = Base64.getEncoder().encodeToString(salt);

        try {
            Files.writeString(saltFile.toPath(), base64Salt);

            // MIN-3 FIX: Restrict permissions — only owner should be able to read the salt file
            saltFile.setReadable(false, false);  // Revoke all read access
            saltFile.setReadable(true, true);    // Grant read access to owner only
            saltFile.setWritable(false);          // Make read-only to prevent tampering

            log.info("🛡️ Generated new PBKDF2 salt and saved to {} (owner-read-only)", SALT_FILE);
            return salt;
        } catch (IOException e) {
            log.error("❌ Failed to write {}, using generated salt in memory ONLY. Will be lost on restart!", SALT_FILE, e);
            return salt;
        }
    }
}
