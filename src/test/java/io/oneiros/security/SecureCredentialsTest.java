package io.oneiros.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SecureCredentials.
 */
class SecureCredentialsTest {

    @Test
    @DisplayName("Should encrypt and decrypt credentials")
    void shouldEncryptAndDecryptCredentials() {
        String original = "my-secret-password";
        SecureCredentials creds = SecureCredentials.from(original);

        char[] decrypted = creds.getPlaintext();
        try {
            assertEquals(original, new String(decrypted));
        } finally {
            Arrays.fill(decrypted, '\0');
        }
    }

    @Test
    @DisplayName("Should create from char array")
    void shouldCreateFromCharArray() {
        char[] original = "test-password".toCharArray();
        SecureCredentials creds = SecureCredentials.from(original);

        // Original should be cleared
        assertEquals('\0', original[0]);

        char[] decrypted = creds.getPlaintext();
        try {
            assertEquals("test-password", new String(decrypted));
        } finally {
            Arrays.fill(decrypted, '\0');
        }
    }

    @Test
    @DisplayName("Should clear credentials")
    void shouldClearCredentials() {
        SecureCredentials creds = SecureCredentials.from("password");

        assertFalse(creds.isCleared());

        creds.clear();

        assertTrue(creds.isCleared());
        assertThrows(IllegalStateException.class, creds::getPlaintext);
    }

    @Test
    @DisplayName("Should work with try-with-resources")
    void shouldWorkWithTryWithResources() {
        SecureCredentials creds;

        try (SecureCredentials c = SecureCredentials.from("password")) {
            creds = c;
            assertFalse(c.isCleared());

            char[] plain = c.getPlaintext();
            Arrays.fill(plain, '\0');
        }

        assertTrue(creds.isCleared());
    }

    @Test
    @DisplayName("Should handle Unicode characters")
    void shouldHandleUnicodeCharacters() {
        String unicode = "パスワード-🔐-Ä";
        SecureCredentials creds = SecureCredentials.from(unicode);

        char[] decrypted = creds.getPlaintext();
        try {
            assertEquals(unicode, new String(decrypted));
        } finally {
            Arrays.fill(decrypted, '\0');
        }
    }

    @Test
    @DisplayName("Should throw on null input")
    void shouldThrowOnNullInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            SecureCredentials.from((String) null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            SecureCredentials.from((char[]) null);
        });
    }

    @Test
    @DisplayName("Should handle empty credentials")
    void shouldHandleEmptyCredentials() {
        SecureCredentials creds = SecureCredentials.from("");

        char[] decrypted = creds.getPlaintext();
        try {
            assertEquals("", new String(decrypted));
        } finally {
            Arrays.fill(decrypted, '\0');
        }
    }

    @Test
    @DisplayName("Should not expose plaintext in memory dump")
    void shouldNotExposePlaintextInMemoryDump() {
        String secret = "super-secret-password-12345";
        SecureCredentials creds = SecureCredentials.from(secret);

        // The SecureCredentials object should not contain the plaintext
        String objString = creds.toString();
        assertFalse(objString.contains(secret), "Plaintext should not be in toString()");

        // After clearing, should be truly gone
        creds.clear();
        assertTrue(creds.isCleared());
    }

    @Test
    @DisplayName("Should allow multiple decryptions")
    void shouldAllowMultipleDecryptions() {
        SecureCredentials creds = SecureCredentials.from("password");

        char[] first = creds.getPlaintext();
        char[] second = creds.getPlaintext();

        try {
            assertEquals(new String(first), new String(second));
        } finally {
            Arrays.fill(first, '\0');
            Arrays.fill(second, '\0');
        }
    }
}
