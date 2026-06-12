package com.github.klboke.nexusplus.persistence.mysql.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashColumns {
  private static final byte[] SEPARATOR = new byte[] {0};

  private HashColumns() {
  }

  public static byte[] componentCoordinateHash(String namespace, String name, String version) {
    return sha256(nullToEmpty(namespace), name, nullToEmpty(version));
  }

  public static byte[] pathHash(String path) {
    return sha256(path);
  }

  public static byte[] blobRefHash(String blobRef) {
    return sha256(blobRef);
  }

  public static byte[] objectKeyHash(String objectKey) {
    return sha256(objectKey);
  }

  public static byte[] sha256(String... parts) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String part : parts) {
        digest.update(nullToEmpty(part).getBytes(StandardCharsets.UTF_8));
        digest.update(SEPARATOR);
      }
      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
