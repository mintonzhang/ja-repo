package com.github.klboke.nexusplus.core.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class ApiKeyTokenPayloads {
  private static final String PREFIX = "{aes-gcm-v1}";
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;
  private static final int GCM_TAG_BITS = 128;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private ApiKeyTokenPayloads() {
  }

  public static String encryptRawToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("rawToken is required");
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      SECURE_RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] cipherText = cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
      byte[] payload = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
      return PREFIX + ENCODER.encodeToString(payload);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt API key token payload", e);
    }
  }

  public static Optional<String> decryptRawToken(String encryptedPayload) {
    if (encryptedPayload == null || !encryptedPayload.startsWith(PREFIX)) {
      return Optional.empty();
    }
    try {
      byte[] payload = DECODER.decode(encryptedPayload.substring(PREFIX.length()));
      if (payload.length <= IV_LENGTH) {
        return Optional.empty();
      }
      byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
      byte[] cipherText = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
      return Optional.of(new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException | GeneralSecurityException e) {
      return Optional.empty();
    }
  }

  private static SecretKeySpec key() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return new SecretKeySpec(digest.digest(secret().getBytes(StandardCharsets.UTF_8)), "AES");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static String secret() {
    return EncryptionSecrets.apiKeyPayloadSecret();
  }
}
