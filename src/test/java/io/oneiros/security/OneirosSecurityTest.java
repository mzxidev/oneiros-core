package io.oneiros.security;

import io.oneiros.annotation.OneirosEncrypted;
import io.oneiros.annotation.OneirosEntity;
import io.oneiros.client.OneirosClient;
import io.oneiros.client.SecureOneirosClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for transparent field-level encryption in OneirosSecurityHandler.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>@OneirosEncrypted fields are encrypted before writing to DB</li>
 *   <li>Encrypted values in DB are decrypted when reading</li>
 *   <li>The user always works with plaintext values</li>
 *   <li>The database only sees ciphertext</li>
 * </ul>
 */
class OneirosSecurityTest {

    private CryptoService cryptoService;
    private PasswordHasher passwordHasher;
    private OneirosSecurityHandler securityHandler;
    private OneirosClient mockClient;
    private SecureOneirosClient secureClient;

    /**
     * Test entity with encrypted fields.
     */
    @OneirosEntity("users")
    static class User {
        private String id;
        private String name;

        @OneirosEncrypted(type = EncryptionType.AES_GCM)
        private String apiKey;

        @OneirosEncrypted(type = EncryptionType.ARGON2)
        private String password;

        public User() {}

        public User(String id, String name, String apiKey, String password) {
            this.id = id;
            this.name = name;
            this.apiKey = apiKey;
            this.password = password;
        }

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    @BeforeEach
    void setUp() {
        // Create config for crypto service
        io.oneiros.core.OneirosConfig config = io.oneiros.core.OneirosConfig.builder()
            .security(io.oneiros.core.OneirosConfig.SecurityConfig.builder()
                .enabled(true)
                .key("test-key-16chars")
                .build())
            .build();

        // Create real crypto services
        cryptoService = new CryptoService(config);
        passwordHasher = new PasswordHasher(EncryptionType.ARGON2);
        securityHandler = new OneirosSecurityHandler(cryptoService, passwordHasher, true);

        // Mock the underlying client
        mockClient = mock(OneirosClient.class);

        // Wrap with security
        secureClient = new SecureOneirosClient(mockClient, securityHandler);
    }

    @Test
    void testEncryptOnWrite_AES() {
        // Given: User with plaintext data
        User user = new User(null, "John Doe", "secret-api-key", "secret-password");
        String originalApiKey = user.getApiKey();
        String originalPassword = user.getPassword();

        // When: Encrypt for writing
        User encrypted = securityHandler.encryptOnWrite(user);

        // Then: Same instance is returned (in-place modification)
        assertSame(user, encrypted, "Should return same instance");

        // And: Encrypted copy has ciphertext
        assertNotNull(encrypted.getApiKey(), "Encrypted apiKey should not be null");
        assertNotEquals(originalApiKey, encrypted.getApiKey(), "ApiKey should be encrypted");
        // AES-GCM encryption returns Base64 encoded string
        assertFalse(encrypted.getApiKey().contains("secret"), "Should not contain plaintext");

        // And: Password is hashed
        assertNotNull(encrypted.getPassword(), "Password should be hashed");
        assertNotEquals(originalPassword, encrypted.getPassword(), "Password should not be plaintext");

        // And: Non-encrypted fields are preserved
        assertEquals("John Doe", encrypted.getName(), "Name should be unchanged");
    }

    @Test
    void testDecryptOnRead_AES() {
        // Given: User with encrypted data
        String plainApiKey = "secret-api-key";
        String encryptedApiKey = cryptoService.encrypt(plainApiKey);

        User encrypted = new User("user:123", "John Doe", encryptedApiKey, "hashed-password");

        // When: Decrypt after reading
        User decrypted = securityHandler.decryptOnRead(encrypted);

        // Then: AES field is decrypted
        assertEquals(plainApiKey, decrypted.getApiKey(), "ApiKey should be decrypted");

        // And: Password hash remains (not reversible)
        assertEquals("hashed-password", decrypted.getPassword(), "Password hash should remain");

        // And: Non-encrypted fields are unchanged
        assertEquals("John Doe", decrypted.getName(), "Name should be unchanged");
    }

    @Test
    void testSecureClient_Create_DatabaseReceivesEncrypted() {
        // Given: User with plaintext data
        User user = new User(null, "Alice", "plain-api-key", "plain-password");

        // Capture what gets sent to database
        AtomicReference<Object> sentToDb = new AtomicReference<>();

        when(mockClient.create(eq("users"), any(), eq(User.class)))
            .thenAnswer(invocation -> {
                // Capture the data argument (which should be encrypted)
                sentToDb.set(invocation.getArgument(1));

                // Return encrypted user from "database"
                User dbUser = (User) invocation.getArgument(1);
                return Mono.just(dbUser);
            });

        // When: Create user through secure client
        User result = secureClient.create("users", user, User.class).block();

        // Then: Database received encrypted data
        User sentUser = (User) sentToDb.get();
        assertNotNull(sentUser, "Data should be sent to database");
        assertNotEquals("plain-api-key", sentUser.getApiKey(), "Database should receive encrypted apiKey");
        assertFalse(sentUser.getApiKey().contains("plain"), "Encrypted data should not contain plaintext");
        assertNotEquals("plain-password", sentUser.getPassword(), "Database should receive hashed password");

        // And: User receives decrypted result
        assertNotNull(result, "Result should not be null");
        assertEquals("plain-api-key", result.getApiKey(), "User should see plaintext apiKey");

        // Note: Original user object is now encrypted (in-place modification)
        assertNotEquals("plain-api-key", user.getApiKey(), "Original is now encrypted");
    }

    @Test
    void testSecureClient_Select_UserReceivesDecrypted() {
        // Given: Database has encrypted user
        String plainApiKey = "secret-key";
        String encryptedApiKey = cryptoService.encrypt(plainApiKey);

        User dbUser = new User("user:123", "Bob", encryptedApiKey, "hashed-password");

        when(mockClient.select(eq("users"), eq(User.class)))
            .thenReturn(Flux.just(dbUser));

        // When: Select through secure client
        List<User> results = secureClient.select("users", User.class)
            .collectList()
            .block();

        // Then: User receives decrypted data
        assertNotNull(results);
        assertEquals(1, results.size());

        User user = results.getFirst();
        assertEquals(plainApiKey, user.getApiKey(), "User should see plaintext apiKey");
        assertEquals("Bob", user.getName(), "Name should be unchanged");
    }

    @Test
    void testSecureClient_Update_DatabaseReceivesEncrypted() {
        // Given: User with updated plaintext data
        User user = new User("user:123", "Updated Name", "new-api-key", "new-password");

        AtomicReference<Object> sentToDb = new AtomicReference<>();

        when(mockClient.update(eq("user:123"), any(), eq(User.class)))
            .thenAnswer(invocation -> {
                sentToDb.set(invocation.getArgument(1));
                User dbUser = (User) invocation.getArgument(1);
                return Flux.just(dbUser);
            });

        // When: Update through secure client
        List<User> results = secureClient.update("user:123", user, User.class)
            .collectList()
            .block();

        // Then: Database received encrypted data
        User sentUser = (User) sentToDb.get();
        assertNotEquals("new-api-key", sentUser.getApiKey(), "Database should receive encrypted apiKey");
        assertFalse(sentUser.getApiKey().contains("new"), "Encrypted data should not contain plaintext");

        // And: User receives decrypted result
        assertNotNull(results);
        assertEquals(1, results.size());
        // Note: The result is decrypted by SecureOneirosClient
        assertEquals("new-api-key", results.getFirst().getApiKey(), "User should see plaintext");
    }

    @Test
    void testSecureClient_Query_UserReceivesDecrypted() {
        // Given: Database returns encrypted users
        String plainApiKey1 = "key1";
        String plainApiKey2 = "key2";
        String encryptedApiKey1 = cryptoService.encrypt(plainApiKey1);
        String encryptedApiKey2 = cryptoService.encrypt(plainApiKey2);

        User dbUser1 = new User("user:1", "Alice", encryptedApiKey1, "hash1");
        User dbUser2 = new User("user:2", "Bob", encryptedApiKey2, "hash2");

        when(mockClient.query(anyString(), eq(User.class)))
            .thenReturn(Flux.just(dbUser1, dbUser2));

        // When: Query through secure client
        List<User> results = secureClient.query("SELECT * FROM users", User.class)
            .collectList()
            .block();

        // Then: All users are decrypted
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(plainApiKey1, results.get(0).getApiKey(), "First user should be decrypted");
        assertEquals(plainApiKey2, results.get(1).getApiKey(), "Second user should be decrypted");
    }

    @Test
    void testSecurityHandler_Disabled_NoEncryption() {
        // Given: Security handler is disabled
        OneirosSecurityHandler disabledHandler = new OneirosSecurityHandler(null, null, false);

        User user = new User(null, "Test", "plain-key", "plain-password");

        // When: Encrypt/decrypt with disabled handler
        User afterEncrypt = disabledHandler.encryptOnWrite(user);
        User afterDecrypt = disabledHandler.decryptOnRead(user);

        // Then: No changes
        assertSame(user, afterEncrypt, "Should return same instance when disabled");
        assertSame(user, afterDecrypt, "Should return same instance when disabled");
        assertEquals("plain-key", user.getApiKey(), "Data should remain plaintext");
    }

    @Test
    void testEncryptOnWrite_NullValue_Skipped() {
        // Given: User with null encrypted field
        User user = new User(null, "Test", null, "password");

        // When: Encrypt
        User encrypted = securityHandler.encryptOnWrite(user);

        // Then: Null is preserved
        assertNull(encrypted.getApiKey(), "Null field should remain null");
        assertNotNull(encrypted.getPassword(), "Non-null field should be processed");
    }

    @Test
    void testSecureClient_FullFlow_CreateAndSelect() {
        // This test demonstrates the full flow from user perspective

        // Step 1: User creates object with plaintext
        User originalUser = new User(null, "Charlie", "my-secret-key", "my-password");

        AtomicReference<Object> sentToDb = new AtomicReference<>();

        // Mock create: capture what goes to DB, return it
        when(mockClient.create(eq("users"), any(), eq(User.class)))
            .thenAnswer(invocation -> {
                User dbUser = (User) invocation.getArgument(1);
                sentToDb.set(dbUser);
                dbUser.setId("user:123");
                return Mono.just(dbUser);
            });

        // Step 2: Create through secure client
        User createdUser = secureClient.create("users", originalUser, User.class).block();

        // Verify: Database received encrypted data
        User dbUser = (User) sentToDb.get();
        assertFalse(dbUser.getApiKey().contains("secret"), "DB should have encrypted data");

        // Verify: User received decrypted data
        assertNotNull(createdUser);
        assertEquals("my-secret-key", createdUser.getApiKey(), "User should see plaintext");

        // Step 3: Mock select to return the encrypted data from DB
        when(mockClient.select(eq("users"), eq(User.class)))
            .thenReturn(Flux.just(dbUser));

        // Step 4: Select through secure client
        List<User> selected = secureClient.select("users", User.class).collectList().block();

        // Verify: User receives decrypted data
        assertNotNull(selected);
        assertEquals(1, selected.size());
        assertEquals("my-secret-key", selected.getFirst().getApiKey(), "Selected user should have plaintext");
        assertEquals("Charlie", selected.getFirst().getName());
        assertEquals("user:123", selected.getFirst().getId());
    }

    @Test
    void testPasswordHashing_NotReversible() {
        // Given: User with password
        User user = new User(null, "Test", "api-key", "plaintext-password");

        // When: Encrypt for writing
        User encrypted = securityHandler.encryptOnWrite(user);

        // Then: Password is hashed (not encrypted)
        assertNotEquals("plaintext-password", encrypted.getPassword(), "Password should be hashed");
        assertTrue(encrypted.getPassword().startsWith("$argon2"), "Should be Argon2 hash");

        // When: Try to "decrypt" hashed password
        User afterDecrypt = securityHandler.decryptOnRead(encrypted);

        // Then: Hash remains (not reversible)
        assertEquals(encrypted.getPassword(), afterDecrypt.getPassword(),
            "Password hash should remain unchanged (not reversible)");
    }
}

