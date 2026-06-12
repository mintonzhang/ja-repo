package com.github.klboke.nexusplus.server.maven;

/**
 * Encodes the scope of a queued metadata rebuild into the {@code scope_key} column of
 * {@code metadata_rebuild_marker}. Two shapes:
 * <ul>
 *   <li>{@code ga:groupId/artifactId} — artifact-level metadata rebuild.</li>
 *   <li>{@code gav:groupId/artifactId/baseVersion} — GAV-SNAPSHOT (base version) rebuild.</li>
 * </ul>
 * Keeping the encoder + parser side by side makes it obvious that every marker the worker dequeues
 * must round-trip back to one of these two forms.
 */
final class MetadataRebuildScope {
  private MetadataRebuildScope() {}

  static String ga(String groupId, String artifactId) {
    return "ga:" + groupId + "/" + artifactId;
  }

  static String gav(String groupId, String artifactId, String baseVersion) {
    return "gav:" + groupId + "/" + artifactId + "/" + baseVersion;
  }

  static Parsed parse(String scopeKey) {
    if (scopeKey == null) return null;
    if (scopeKey.startsWith("ga:")) {
      String rest = scopeKey.substring(3);
      int slash = rest.lastIndexOf('/');
      if (slash <= 0) return null;
      return new Parsed(Kind.GA, rest.substring(0, slash), rest.substring(slash + 1), null);
    }
    if (scopeKey.startsWith("gav:")) {
      String rest = scopeKey.substring(4);
      int second = rest.lastIndexOf('/');
      if (second <= 0) return null;
      String baseVersion = rest.substring(second + 1);
      String head = rest.substring(0, second);
      int first = head.lastIndexOf('/');
      if (first <= 0) return null;
      return new Parsed(Kind.GAV, head.substring(0, first), head.substring(first + 1), baseVersion);
    }
    return null;
  }

  enum Kind { GA, GAV }

  record Parsed(Kind kind, String groupId, String artifactId, String baseVersion) {}
}
