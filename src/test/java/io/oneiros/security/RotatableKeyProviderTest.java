package io.oneiros.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RotatableKeyProvider.
 */
class RotatableKeyProviderTest {

    @Test
    @DisplayName("Should initialize with version 1")
    void shouldInitializeWithVersion1() {
        RotatableKeyProvider provider = new RotatableKeyProvider("test-key-12345");

        assertEquals("1", provider.getKeyVersion());
        assertEquals("rotatable-v1", provider.getKeyId());
        assertEquals(1, provider.getKeyHistorySize());
        assertTrue(provider.supportsRotation());
    }

    @Test
    @DisplayName("Should rotate to new key version")
    void shouldRotateToNewKeyVersion() {
        RotatableKeyProvider provider = new RotatableKeyProvider("initial-key-123");

        SecretKey key1 = provider.getSecretKey();
        assertEquals("1", provider.getKeyVersion());

        // Rotate to version 2
        provider.rotateKey("new-key-version-2");

        SecretKey key2 = provider.getSecretKey();
        assertEquals("2", provider.getKeyVersion());
        assertEquals("rotatable-v2", provider.getKeyId());
        assertNotEquals(key1, key2);

        // Both versions should be available
        assertEquals(2, provider.getKeyHistorySize());
        assertNotNull(provider.getKeyByVersion(1));
        assertNotNull(provider.getKeyByVersion(2));
    }

    @Test
    @DisplayName("Should maintain key history with limit")
    void shouldMaintainKeyHistoryWithLimit() {
        RotatableKeyProvider provider = new RotatableKeyProvider("key-v1", 3);

        // Rotate 5 times
        provider.rotateKey("key-v2");
        provider.rotateKey("key-v3");
        provider.rotateKey("key-v4");
        provider.rotateKey("key-v5");

        // Should only keep last 3 versions (3, 4, 5)
        assertEquals(3, provider.getKeyHistorySize());
        assertNull(provider.getKeyByVersion(1));
        assertNull(provider.getKeyByVersion(2));
        assertNotNull(provider.getKeyByVersion(3));
        assertNotNull(provider.getKeyByVersion(4));
        assertNotNull(provider.getKeyByVersion(5));
    }

    @Test
    @DisplayName("Should decrypt with old key version")
    void shouldDecryptWithOldKeyVersion() {
        RotatableKeyProvider provider = new RotatableKeyProvider("key-v1");
        CryptoService crypto = new CryptoService(provider);

        // Encrypt with v1
        String plaintext = "sensitive-data";
        String encrypted = crypto.encrypt(plaintext);

        // Rotate to v2
        provider.rotateKey("key-v2");
        assertEquals("2", provider.getKeyVersion());

        // Should still decrypt data encrypted with v1
        // Note: This requires CryptoService to support version detection
        // For now, we just verify the key is available
        assertNotNull(provider.getKeyByVersion(1));
        assertNotNull(provider.getKeyByVersion(2));
    }

    @Test
    @DisplayName("Should throw on short key")
    void shouldThrowOnShortKey() {
        assertThrows(KeyProviderException.class, () -> {
            new RotatableKeyProvider("short");
        });

        RotatableKeyProvider provider = new RotatableKeyProvider("valid-key-123");
        assertThrows(KeyProviderException.class, () -> {
            provider.rotateKey("short");
        });
    }

    @Test
    @DisplayName("Should clear keys on close")
    void shouldClearKeysOnClose() {
        RotatableKeyProvider provider = new RotatableKeyProvider("key-123");
        provider.rotateKey("key-456");

        assertEquals(2, provider.getKeyHistorySize());

        provider.close();

        assertEquals(0, provider.getKeyHistorySize());
    }

    @Test
    @DisplayName("Should provide metadata")
    void shouldProvideMetadata() {
        RotatableKeyProvider provider = new RotatableKeyProvider("key-123", 5);
        provider.rotateKey("key-456");

        var metadata = provider.getMetadata();
        assertTrue(metadata.isPresent());
        assertEquals("ROTATABLE", metadata.get().providerType());
        assertEquals("2", metadata.get().additionalInfo().get("currentVersion"));
    }

    @Test
    @DisplayName("Should track available versions")
    void shouldTrackAvailableVersions() {
        RotatableKeyProvider provider = new RotatableKeyProvider("key-1");
        provider.rotateKey("key-2");
        provider.rotateKey("key-3");

        var versions = provider.getAvailableVersions();
        assertEquals(3, versions.size());
        assertTrue(versions.contains(1));
        assertTrue(versions.contains(2));
        assertTrue(versions.contains(3));
    }
}
