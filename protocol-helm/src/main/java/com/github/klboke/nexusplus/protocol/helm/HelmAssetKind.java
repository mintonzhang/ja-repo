package com.github.klboke.nexusplus.protocol.helm;

import java.util.Locale;

public enum HelmAssetKind {
  INDEX("index.yaml", ".yaml", "text/x-yaml", true),
  PACKAGE(null, ".tgz", "application/gzip", false),
  PROVENANCE(null, ".tgz.prov", "application/octet-stream", false);

  private final String fixedPath;
  private final String extension;
  private final String contentType;
  private final boolean metadata;

  HelmAssetKind(String fixedPath, String extension, String contentType, boolean metadata) {
    this.fixedPath = fixedPath;
    this.extension = extension;
    this.contentType = contentType;
    this.metadata = metadata;
  }

  public String fixedPath() {
    return fixedPath;
  }

  public String extension() {
    return extension;
  }

  public String contentType() {
    return contentType;
  }

  public boolean metadata() {
    return metadata;
  }

  public static HelmAssetKind fromPath(String path) {
    String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT);
    if ("index.yaml".equals(normalized)) return INDEX;
    if (normalized.endsWith(PROVENANCE.extension)) return PROVENANCE;
    if (normalized.endsWith(PACKAGE.extension)) return PACKAGE;
    throw new IllegalArgumentException("Unsupported Helm asset path: " + path);
  }
}
