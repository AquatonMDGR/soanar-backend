package com.soanar.service.impl;

import com.soanar.service.EncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Implementation of encryption service using AES encryption
 */
@Service
public class EncryptionServiceImpl implements EncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionServiceImpl.class);
    private static final String ENCRYPTION_PREFIX = "ENC:";
    private static final String ALGORITHM = "AES";

    @Value("${encryption.key:defaultEncryptionKeyChangeInProduction}")
    private String encryptionKey;

    private SecretKey secretKey;

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            if (secretKey == null) {
                initializeKey();
            }

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getEncoder().encodeToString(encryptedBytes);

            return ENCRYPTION_PREFIX + encoded;
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt value", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }

        try {
            if (secretKey == null) {
                initializeKey();
            }

            // Remove encryption prefix if present
            String encrypted = ciphertext.startsWith(ENCRYPTION_PREFIX) ?
                    ciphertext.substring(ENCRYPTION_PREFIX.length()) : ciphertext;

            byte[] decodedBytes = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt value", e);
        }
    }

    @Override
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTION_PREFIX);
    }

    private synchronized void initializeKey() {
        if (secretKey == null) {
            try {
                // Derive key from password using SHA-256
                byte[] decodedKey = deriveKey(encryptionKey);
                secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
                logger.info("Encryption key initialized");
            } catch (Exception e) {
                logger.error("Failed to initialize encryption key", e);
                throw new RuntimeException("Failed to initialize encryption key", e);
            }
        }
    }

    /**
     * Derive a 128-bit key from the password using SHA-256
     */
    private byte[] deriveKey(String password) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] hashBytes = sha256.digest(passwordBytes);

        // Return first 16 bytes (128 bits) for AES-128
        byte[] key = new byte[16];
        System.arraycopy(hashBytes, 0, key, 0, 16);

        return key;
    }
}
