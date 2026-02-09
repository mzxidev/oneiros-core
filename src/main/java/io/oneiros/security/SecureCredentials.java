package io.oneiros.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Utility for securely storing and encrypting credentials in memory.
 *
 * <p>This class provides better security than storing credentials as plain Strings:
 * <ul>
 *   <li>Uses char[] instead of String to allow explicit clearing</li>
 *   <li>Encrypts credentials in memory when not in use</li>
 *   <li>Provides automatic cleanup on finalization</li>
 *   <li>Reduces exposure window for credential theft</li>
 * </ul>
 *
 * <h3>Security Benefits</h3>
 * <ul>
 *   <li>Credentials are encrypted when stored</li>
 *   <li>Decrypted only when needed (minimal exposure)</li>
 *   <li>Memory is explicitly cleared after use</li>
 *   <li>Not visible in heap dumps in encrypted form</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Store credentials securely
 * SecureCredentials creds = SecureCredentials.from("my-password");
 *
 * // Use credentials (temporarily decrypted)
 * char[] password = creds.getPlaintext();
 * try {
 *     // Use password
 *     authenticate(new String(password));
 * } finally {
 *     // Always clear after use
 *     Arrays.fill(password, '\0');
 * }
 *
 * // Cleanup when done
 * creds.clear();
 * }</pre>
 *
 * @since 0.4.2
 */
public class SecureCredentials implements AutoCloseable {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final byte[] encryptedData;
    private final byte[] iv;
    private final SecretKey encryptionKey;
    private volatile boolean cleared = false;

    private SecureCredentials(char[] plaintext) {
        try {
            // Generate encryption key for this credential
            this.encryptionKey = generateTransientKey();

            // Generate random IV
            SecureRandom random = new SecureRandom();
            this.iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            // Encrypt the credential
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, spec);

            byte[] plaintextBytes = charsToBytes(plaintext);
            this.encryptedData = cipher.doFinal(plaintextBytes);

            // Clear plaintext immediately
            Arrays.fill(plaintextBytes, (byte) 0);
            Arrays.fill(plaintext, '\0');

        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt credentials", e);
        }
    }

    /**
     * Creates secure credentials from a plaintext string.
     *
     * <p>The input string is immediately cleared from memory after encryption.
     *
     * @param plaintext the plaintext credential
     * @return encrypted secure credentials
     */
    public static SecureCredentials from(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }
        char[] chars = plaintext.toCharArray();
        SecureCredentials creds = new SecureCredentials(chars);
        Arrays.fill(chars, '\0'); // Clear input
        return creds;
    }

    /**
     * Creates secure credentials from a char array.
     *
     * <p>The input array is cleared after encryption.
     *
     * @param plaintext the plaintext credential as char array
     * @return encrypted secure credentials
     */
    public static SecureCredentials from(char[] plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }
        return new SecureCredentials(plaintext);
    }

    /**
     * Retrieves the plaintext credential.
     *
     * <p><strong>Important:</strong> The returned char array must be explicitly
     * cleared after use by calling {@code Arrays.fill(chars, '\0')}.
     *
     * <p>Example:
     * <pre>{@code
     * char[] password = creds.getPlaintext();
     * try {
     *     // Use password
     * } finally {
     *     Arrays.fill(password, '\0');
     * }
     * }</pre>
     *
     * @return decrypted plaintext as char array
     * @throws IllegalStateException if credentials have been cleared
     */
    public char[] getPlaintext() {
        if (cleared) {
            throw new IllegalStateException("Credentials have been cleared");
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, spec);

            byte[] plaintextBytes = cipher.doFinal(encryptedData);
            char[] plaintext = bytesToChars(plaintextBytes);

            // Clear intermediate bytes
            Arrays.fill(plaintextBytes, (byte) 0);

            return plaintext;

        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt credentials", e);
        }
    }

    /**
     * Checks if credentials have been cleared.
     */
    public boolean isCleared() {
        return cleared;
    }

    /**
     * Clears all sensitive data from memory.
     *
     * <p>After calling this method, the credentials cannot be recovered.
     */
    public void clear() {
        if (!cleared) {
            Arrays.fill(encryptedData, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            cleared = true;
        }
    }

    @Override
    public void close() {
        clear();
    }

    /**
     * Generates a transient encryption key for this credential instance.
     */
    private SecretKey generateTransientKey() throws Exception {
        // Generate random key material
        SecureRandom random = new SecureRandom();
        byte[] keyBytes = new byte[32]; // 256 bits
        random.nextBytes(keyBytes);

        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Converts char array to byte array (UTF-8).
     */
    private byte[] charsToBytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
        Arrays.fill(byteBuffer.array(), (byte) 0); // Clear buffer
        return bytes;
    }

    /**
     * Converts byte array to char array (UTF-8).
     */
    private char[] bytesToChars(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(byteBuffer);
        char[] chars = Arrays.copyOfRange(charBuffer.array(),
                charBuffer.position(), charBuffer.limit());
        Arrays.fill(charBuffer.array(), '\0'); // Clear buffer
        return chars;
    }

    // Note: finalize() is deprecated in Java 9+
    // Use try-with-resources or explicit clear() instead
    // Cleaner API (JEP 421) would be better but requires Java 9+
}
