package com.github.klboke.nexusplus.protocol.maven.policy;

public enum LayoutPolicy {
  STRICT,
  PERMISSIVE;

  public static LayoutPolicy parse(String value) {
    if (value == null || value.isBlank()) return STRICT;
    return LayoutPolicy.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
  }
}
