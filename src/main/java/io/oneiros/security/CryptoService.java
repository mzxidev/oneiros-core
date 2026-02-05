package io.oneiros.security;

import io.oneiros.config.OneirosProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
public class CryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // Bit
    private static final int IV_LENGTH = 12; // Bytes (Standard für GCM)

    private final boolean enabled;
    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoService(OneirosProperties properties) {
        this.enabled = properties.getSecurity().isEnabled();
        String keyString = properties.getSecurity().getKey();

        if (enabled) {
            if (keyString == null || keyString.length() < 8) {
                throw new IllegalArgumentException("❌ Oneiros Security is enabled but Key is too short or missing!");
            }
            // Wir hashen den User-Key mit SHA-256, um sicherzustellen, dass er genau 32 Bytes (256 Bit) hat
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

    public String encrypt(String plainText) {
        if (!enabled || plainText == null) return plainText;
        try {
            // 1. IV generieren (Zufall pro Verschlüsselung!)
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            // 2. Cipher init
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // 3. Verschlüsseln
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 4. IV und CipherText zusammenpacken (wir brauchen den IV zum Entschlüsseln)
            // Format: Base64(IV + CipherText)
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (!enabled || encryptedText == null) return encryptedText;
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            // 1. IV extrahieren (die ersten 12 Bytes)
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);

            // 2. CipherText extrahieren (der Rest)
            int cipherTextLength = decoded.length - IV_LENGTH;
            byte[] cipherText = new byte[cipherTextLength];
            System.arraycopy(decoded, IV_LENGTH, cipherText, 0, cipherTextLength);

            // 3. Entschlüsseln
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to decrypt data: {}", e.getMessage());
            // Fallback: Vielleicht war es gar nicht verschlüsselt? Oder falscher Key?
            return encryptedText;
        }
    }
}