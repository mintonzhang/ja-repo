package com.github.klboke.nexusplus.server.goartifact;

import java.util.Set;

public record GoPath(String path, String module, String version, GoAssetKind kind) {
  private static final String VERSION_MARKER = "/@v/";
  private static final Set<String> VERSIONED_EXTENSIONS = Set.of("info", "mod", "zip");

  public static GoPath parse(String rawPath) {
    String path = trimLeadingSlash(rawPath == null ? "" : rawPath);
    if (path.isBlank() || path.endsWith("/")) {
      throw new IllegalArgumentException("Invalid Go module proxy path: " + path);
    }
    if (path.endsWith("/@latest")) {
      String module = path.substring(0, path.length() - "/@latest".length());
      return new GoPath(path, requireModule(module, path), null, GoAssetKind.LATEST);
    }
    if (path.endsWith("/@v/list")) {
      String module = path.substring(0, path.length() - "/@v/list".length());
      return new GoPath(path, requireModule(module, path), null, GoAssetKind.LIST);
    }

    int marker = path.lastIndexOf(VERSION_MARKER);
    if (marker <= 0) {
      throw new IllegalArgumentException("Invalid Go module proxy path: " + path);
    }
    String module = path.substring(0, marker);
    String file = path.substring(marker + VERSION_MARKER.length());
    int dot = file.lastIndexOf('.');
    if (dot <= 0 || dot == file.length() - 1) {
      throw new IllegalArgumentException("Invalid Go module version path: " + path);
    }
    String version = file.substring(0, dot);
    String extension = file.substring(dot + 1);
    if (!VERSIONED_EXTENSIONS.contains(extension)) {
      throw new IllegalArgumentException("Unsupported Go module extension: " + extension);
    }
    return new GoPath(path, requireModule(module, path), version, GoAssetKind.fromExtension(extension));
  }

  public boolean hasComponent() {
    return kind == GoAssetKind.PACKAGE || kind == GoAssetKind.INFO || kind == GoAssetKind.MODULE;
  }

  public boolean metadata() {
    return kind != GoAssetKind.PACKAGE;
  }

  public String fileName() {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  public String contentType() {
    return switch (kind) {
      case PACKAGE -> "application/zip";
      case LIST -> "application/json";
      case INFO, LATEST, MODULE -> "text/plain";
    };
  }

  private static String trimLeadingSlash(String value) {
    String result = value;
    while (result.startsWith("/")) {
      result = result.substring(1);
    }
    return result;
  }

  private static String requireModule(String module, String path) {
    if (module == null || module.isBlank() || module.contains("@v")) {
      throw new IllegalArgumentException("Invalid Go module path: " + path);
    }
    return module;
  }
}
