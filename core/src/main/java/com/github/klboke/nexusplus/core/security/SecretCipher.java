package com.github.klboke.nexusplus.core.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Reversible authenticated encryption (AES-256-GCM) for sensitive values that must be
 * recovered in plaintext at runtime — for example blob-store credentials — as opposed to
 * one-way password hashes.
 *
 * <p>Ciphertext is tagged with {@link #PREFIX}, so {@link #decrypt(String)} can transparently
 * return legacy plaintext that predates encryption and {@link #encrypt(String)} is idempotent
 * (an already-encrypted value is returned unchanged). This makes the cipher safe to drop onto a
 * column that may already hold a mix of plaintext and ciphertext rows.
 */
public final class SecretCipher {
  private static final String PREFIX = "{aes-gcm-v1}";
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;
  private static final int GCM_TAG_BITS = 128;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final SecretKeySpec key;

  public SecretCipher(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException("secret is required");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      this.key = new SecretKeySpec(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  public static boolean isEncrypted(String value) {
    return value != null && value.startsWith(PREFIX);
  }

  /**
   * Encrypts a plaintext value. {@code null}, empty, and already-encrypted values are returned
   * unchanged so callers can apply this blindly over a column without double-encrypting.
   */
  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isEmpty() || isEncrypted(plaintext)) {
      return plaintext;
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      SECURE_RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] payload = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
      return PREFIX + ENCODER.encodeToString(payload);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt secret", e);
    }
  }

  /**
   * Decrypts a {@link #PREFIX}-tagged value. Values without the prefix are assumed to be legacy
   * plaintext and returned unchanged; this keeps reads working while existing rows are migrated
   * to ciphertext on their next write.
   */
  public String decrypt(String value) {
    if (!isEncrypted(value)) {
      return value;
    }
    try {
      byte[] payload = DECODER.decode(value.substring(PREFIX.length()));
      if (payload.length <= IV_LENGTH) {
        throw new IllegalStateException("Encrypted secret payload is truncated");
      }
      byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
      byte[] cipherText = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException | GeneralSecurityException e) {
      throw new IllegalStateException("Failed to decrypt secret", e);
    }
  }
}
