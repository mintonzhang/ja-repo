package com.github.klboke.nexusplus.protocol.maven;

import java.util.Locale;
import java.util.Map;

public final class MavenContentType {
  private MavenContentType() {}

  public static final String DEFAULT = "application/octet-stream";
  public static final String CHECKSUM = "text/plain";
  public static final String XML = "application/xml";

  private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
      Map.entry("jar", "application/java-archive"),
      Map.entry("war", "application/java-archive"),
      Map.entry("ear", "application/java-archive"),
      Map.entry("pom", "application/xml"),
      Map.entry("xml", "application/xml"),
      Map.entry("zip", "application/zip"),
      Map.entry("tar", "application/x-tar"),
      Map.entry("tar.gz", "application/x-gzip"),
      Map.entry("tgz", "application/x-gzip"),
      Map.entry("gz", "application/x-gzip"),
      Map.entry("md5", CHECKSUM),
      Map.entry("sha1", CHECKSUM),
      Map.entry("sha256", CHECKSUM),
      Map.entry("sha512", CHECKSUM),
      Map.entry("asc", "text/plain"),
      Map.entry("txt", "text/plain"));

  public static String forFileName(String fileName) {
    if (fileName == null || fileName.isEmpty()) return DEFAULT;
    String lower = fileName.toLowerCase(Locale.ROOT);
    int dot = lower.lastIndexOf('.');
    if (dot < 0) return DEFAULT;
    String ext = lower.substring(dot + 1);
    String compound = null;
    int prev = lower.lastIndexOf('.', dot - 1);
    if (prev >= 0) {
      compound = lower.substring(prev + 1);
    }
    if (compound != null && BY_EXTENSION.containsKey(compound)) {
      return BY_EXTENSION.get(compound);
    }
    return BY_EXTENSION.getOrDefault(ext, DEFAULT);
  }
}
