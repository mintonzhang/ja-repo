package com.github.klboke.nexusplus.server.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class SecurityHashing {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final String PBKDF2_PREFIX = "{PBKDF2WithHmacSHA256}";

  private SecurityHashing() {
  }

  static String hashPassword(String password) {
    try {
      byte[] salt = new byte[16];
      SECURE_RANDOM.nextBytes(salt);
      int iterations = 120_000;
      KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
      byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
      return PBKDF2_PREFIX + iterations + ":"
          + Base64.getEncoder().encodeToString(salt) + ":"
          + Base64.getEncoder().encodeToString(encoded);
    } catch (Exception e) {
      throw new SecurityValidationException("Failed to hash password", e);
    }
  }

  static boolean verifyPassword(String storedHash, String submittedPassword) {
    if (storedHash == null || storedHash.isBlank() || submittedPassword == null) {
      return false;
    }
    try {
      if (storedHash.startsWith(PBKDF2_PREFIX)) {
        return verifyPbkdf2(storedHash, submittedPassword);
      }
      if (storedHash.startsWith("$shiro1$")) {
        return verifyShiro1(storedHash, submittedPassword);
      }
      if (storedHash.matches("(?i)^[0-9a-f]{32}$")) {
        return constantTimeEquals(hex(md5(submittedPassword)), storedHash.toLowerCase());
      }
      if (storedHash.matches("(?i)^[0-9a-f]{40}$")) {
        return constantTimeEquals(hex(sha1(submittedPassword)), storedHash.toLowerCase());
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  static boolean needsRehash(String storedHash) {
    return storedHash == null || !storedHash.startsWith(PBKDF2_PREFIX);
  }

  static String sha256(String value) {
    try {
      return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new SecurityValidationException("Failed to hash API key", e);
    }
  }

  private static boolean verifyPbkdf2(String storedHash, String submittedPassword) throws Exception {
    String encoded = storedHash.substring(PBKDF2_PREFIX.length());
    String[] parts = encoded.split(":", -1);
    if (parts.length != 3) {
      return false;
    }
    int iterations = Integer.parseInt(parts[0]);
    byte[] salt = Base64.getDecoder().decode(parts[1]);
    byte[] expected = Base64.getDecoder().decode(parts[2]);
    KeySpec spec = new PBEKeySpec(submittedPassword.toCharArray(), salt, iterations, expected.length * 8);
    byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    return MessageDigest.isEqual(actual, expected);
  }

  private static boolean verifyShiro1(String storedHash, String submittedPassword) throws Exception {
    String[] parts = storedHash.split("\\$", -1);
    if (parts.length != 6 || !"shiro1".equals(parts[1])) {
      return false;
    }
    String algorithm = parts[2];
    int iterations = Integer.parseInt(parts[3]);
    byte[] salt = parts[4].isEmpty() ? null : Base64.getDecoder().decode(parts[4]);
    byte[] expected = Base64.getDecoder().decode(parts[5]);
    byte[] actual = shiroHash(algorithm, submittedPassword.getBytes(StandardCharsets.UTF_8), salt, iterations);
    return MessageDigest.isEqual(actual, expected);
  }

  private static byte[] shiroHash(String algorithm, byte[] source, byte[] salt, int iterations) throws Exception {
    MessageDigest digest = MessageDigest.getInstance(algorithm);
    if (salt != null) {
      digest.reset();
      digest.update(salt);
    }
    byte[] hashed = digest.digest(source);
    for (int i = 1; i < Math.max(iterations, 1); i++) {
      digest.reset();
      hashed = digest.digest(hashed);
    }
    return hashed;
  }

  private static byte[] md5(String value) throws Exception {
    return MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] sha1(String value) throws Exception {
    return MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }

  private static boolean constantTimeEquals(String left, String right) {
    return MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8),
        right.getBytes(StandardCharsets.UTF_8));
  }
}
