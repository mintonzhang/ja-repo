package com.github.klboke.nexusplus.compat;

import java.util.Optional;

final class CompatDefaults {
  static final String NEXUS_BASE_URL = "http://localhost:28090";
  static final String NEXUS_USERNAME = "admin";
  static final String NEXUS_PASSWORD = "123456";
  static final String NEXUS_PLUS_BASE_URL = "http://127.0.0.1:18090";
  static final String NEXUS_PLUS_USERNAME = "admin";
  static final String NEXUS_PLUS_PASSWORD = "123456";

  private CompatDefaults() {
  }

  static Optional<String> nexusBaseUrl() {
    return Optional.of(stripTrailingSlash(setting("compat.nexus.baseUrl", "NEXUS_COMPAT_BASE_URL")
        .orElse(NEXUS_BASE_URL)));
  }

  static Optional<String> nexusPlusBaseUrl() {
    return Optional.of(stripTrailingSlash(setting("compat.nexusPlus.baseUrl", "NEXUS_PLUS_COMPAT_BASE_URL")
        .orElse(NEXUS_PLUS_BASE_URL)));
  }

  static Optional<String> nexusUsername() {
    return Optional.of(setting("compat.nexus.username", "NEXUS_COMPAT_USERNAME")
        .orElse(NEXUS_USERNAME));
  }

  static Optional<String> nexusPassword() {
    return Optional.of(setting("compat.nexus.password", "NEXUS_COMPAT_PASSWORD")
        .orElse(NEXUS_PASSWORD));
  }

  static Optional<String> nexusPlusUsername() {
    return Optional.of(setting("compat.nexusPlus.username", "NEXUS_PLUS_COMPAT_USERNAME")
        .orElse(NEXUS_PLUS_USERNAME));
  }

  static Optional<String> nexusPlusPassword() {
    return Optional.of(setting("compat.nexusPlus.password", "NEXUS_PLUS_COMPAT_PASSWORD")
        .orElse(NEXUS_PLUS_PASSWORD));
  }

  static Optional<String> setting(String property, String env) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      value = System.getenv(env);
    }
    return value == null || value.isBlank()
        ? Optional.empty()
        : Optional.of(value.trim());
  }

  static String stripTrailingSlash(String value) {
    String result = value;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
