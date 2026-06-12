package com.github.klboke.nexusplus.storage.file;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

final class FileBlobObjectKeys {
  static final int MAX_COLLISION_RETRIES = 8;

  private FileBlobObjectKeys() {
  }

  static String immutableObjectKey(String repository, String sha256) {
    String repoPart = encodeSegment(repository);
    String digestPart = digestSegment(sha256);
    String shardA = digestPart.length() >= 2 ? digestPart.substring(0, 2) : "xx";
    String shardB = digestPart.length() >= 4 ? digestPart.substring(2, 4) : "xx";
    return "repositories/" + repoPart + "/blobs/v2/" + shardA + "/" + shardB
        + "/" + digestPart + "/" + UUID.randomUUID();
  }

  private static String encodeSegment(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static String digestSegment(String value) {
    if (value == null || value.isBlank()) {
      return "unverified";
    }
    String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-f0-9]", "");
    return normalized.isBlank() ? "unverified" : normalized;
  }
}
