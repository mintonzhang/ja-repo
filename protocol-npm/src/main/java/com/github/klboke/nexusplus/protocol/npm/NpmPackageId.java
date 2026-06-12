package com.github.klboke.nexusplus.protocol.npm;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * npm package identifier. Mirrors Nexus's two supported shapes: {@code name} and
 * {@code @scope/name}. The scope is stored without the leading {@code @}.
 */
public record NpmPackageId(String scope, String name) implements Comparable<NpmPackageId> {
  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._~!$&'()*+,;=:@%-]+$");

  public NpmPackageId {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("npm package name is required");
    }
    if (name.startsWith(".") || name.startsWith("_")) {
      throw new IllegalArgumentException("npm package name cannot start with '.' or '_': " + name);
    }
    if (!SAFE.matcher(name).matches()) {
      throw new IllegalArgumentException("npm package name has unsafe characters: " + name);
    }
    if (scope != null) {
      if (scope.isBlank()) {
        throw new IllegalArgumentException("npm package scope cannot be blank");
      }
      if (scope.startsWith(".") || scope.startsWith("_")) {
        throw new IllegalArgumentException("npm package scope cannot start with '.' or '_': " + scope);
      }
      if (!SAFE.matcher(scope).matches()) {
        throw new IllegalArgumentException("npm package scope has unsafe characters: " + scope);
      }
    }
    String id = scope == null ? name : "@" + scope + "/" + name;
    if (id.length() >= 214) {
      throw new IllegalArgumentException("npm package id must be shorter than 214 characters: " + id);
    }
  }

  public String id() {
    return scope == null ? name : "@" + scope + "/" + name;
  }

  public String tarballPath(String tarballName) {
    return id() + "/-/" + tarballName;
  }

  public static NpmPackageId parse(String raw) {
    String value = decode(Objects.requireNonNull(raw, "raw")).trim();
    while (value.startsWith("/")) value = value.substring(1);
    while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
    if (value.startsWith("@")) {
      int slash = value.indexOf('/');
      if (slash < 0) {
        throw new IllegalArgumentException("scoped npm package must be @scope/name: " + raw);
      }
      return new NpmPackageId(value.substring(1, slash), value.substring(slash + 1));
    }
    if (value.contains("/")) {
      throw new IllegalArgumentException("unscoped npm package cannot contain '/': " + raw);
    }
    return new NpmPackageId(null, value);
  }

  static String decode(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    byte[] decoded = new byte[bytes.length];
    int out = 0;
    for (int i = 0; i < bytes.length; i++) {
      byte b = bytes[i];
      if (b == '%' && i + 2 < bytes.length) {
        int hi = hex(bytes[i + 1]);
        int lo = hex(bytes[i + 2]);
        if (hi >= 0 && lo >= 0) {
          decoded[out++] = (byte) ((hi << 4) + lo);
          i += 2;
          continue;
        }
      }
      decoded[out++] = b;
    }
    return new String(decoded, 0, out, StandardCharsets.UTF_8);
  }

  private static int hex(byte b) {
    if (b >= '0' && b <= '9') return b - '0';
    if (b >= 'a' && b <= 'f') return b - 'a' + 10;
    if (b >= 'A' && b <= 'F') return b - 'A' + 10;
    return -1;
  }

  @Override
  public String toString() {
    return id();
  }

  @Override
  public int compareTo(NpmPackageId other) {
    return id().compareTo(other.id());
  }
}
