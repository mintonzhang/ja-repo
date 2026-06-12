package com.github.klboke.nexusplus.migration.nexus.security;

import java.util.Locale;

final class NexusSecuritySources {
  static final String DEFAULT_SOURCE = "Local";
  static final String DEFAULT_ANONYMOUS_REALM_NAME = "NexusAuthorizingRealm";

  private NexusSecuritySources() {
  }

  static String normalizeSource(String source) {
    String normalized = blankToNull(source);
    if (normalized == null) {
      return DEFAULT_SOURCE;
    }
    String lower = normalized.toLowerCase(Locale.ROOT);
    if ("default".equals(lower)
        || "nexus".equals(lower)
        || "local".equals(lower)
        || "nexusauthenticatingrealm".equals(lower)
        || "nexusauthorizingrealm".equals(lower)) {
      return DEFAULT_SOURCE;
    }
    if ("ldap".equals(lower) || "ldaprealm".equals(lower)) {
      return "LDAP";
    }
    if ("oidc".equals(lower) || "oidcrealm".equals(lower)) {
      return "OIDC";
    }
    return normalized;
  }

  static String sourceForRealmName(String realmName) {
    return normalizeSource(defaultString(realmName, DEFAULT_ANONYMOUS_REALM_NAME));
  }

  static String defaultString(String value, String fallback) {
    String normalized = blankToNull(value);
    return normalized == null ? fallback : normalized;
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
