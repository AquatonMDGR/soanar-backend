package com.soanar.service;

/**
 * Encryption/Decryption service for storing sensitive credentials
 */
public interface EncryptionService {

    /**
     * Encrypt plaintext using application encryption key
     */
    String encrypt(String plaintext);

    /**
     * Decrypt ciphertext using application encryption key
     */
    String decrypt(String ciphertext);

    /**
     * Check if a string is encrypted (starts with encryption prefix)
     */
    boolean isEncrypted(String value);
}
