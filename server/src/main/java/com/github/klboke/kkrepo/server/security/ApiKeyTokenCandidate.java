package com.github.klboke.kkrepo.server.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

record ApiKeyTokenCandidate(String domain, String tokenMaterial) {
  private static final String NPM_TOKEN_DOMAIN = "NpmToken";
  private static final String CARGO_TOKEN_DOMAIN = "CargoToken";

  static List<ApiKeyTokenCandidate> fromPresentedToken(String token) {
    return fromPresentedToken(token, false);
  }

  static List<ApiKeyTokenCandidate> fromPresentedCargoToken(String token) {
    return fromPresentedToken(token, true);
  }

  private static List<ApiKeyTokenCandidate> fromPresentedToken(String token, boolean preferCargoDomain) {
    String value = normalize(token);
    if (value == null) {
      return List.of();
    }
    LinkedHashMap<String, ApiKeyTokenCandidate> candidates = new LinkedHashMap<>();
    int separator = value.indexOf('.');
    if (separator > 0 && separator < value.length() - 1) {
      String domain = normalize(value.substring(0, separator));
      String rawToken = normalize(value.substring(separator + 1));
      if (domain != null && rawToken != null) {
        add(candidates, domain, rawToken);
      }
    } else {
      // Bare tokens are checked in Nexus-compatible token domains before the full-token fallback.
      add(candidates, preferCargoDomain ? CARGO_TOKEN_DOMAIN : NPM_TOKEN_DOMAIN, value);
      add(candidates, preferCargoDomain ? NPM_TOKEN_DOMAIN : CARGO_TOKEN_DOMAIN, value);
    }
    add(candidates, null, value);
    return new ArrayList<>(candidates.values());
  }

  boolean domainScoped() {
    return domain != null;
  }

  private static void add(
      LinkedHashMap<String, ApiKeyTokenCandidate> candidates,
      String domain,
      String tokenMaterial) {
    String value = normalize(tokenMaterial);
    if (value == null) {
      return;
    }
    String key = (domain == null ? "" : domain) + "::" + value;
    candidates.putIfAbsent(key, new ApiKeyTokenCandidate(domain, value));
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
