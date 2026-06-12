package com.github.klboke.nexusplus.server.npm;

import com.github.klboke.nexusplus.protocol.npm.NpmPackageId;
import java.util.Locale;

public enum NpmPackumentVariant {
  FULL("package-root", null),
  INSTALL_V1("package-root-install-v1", "application/vnd.npm.install-v1+json");

  private static final String INSTALL_V1_CACHE_PREFIX =
      ".nexus-plus/cache/npm/group-packument/install-v1/";

  private final String assetKind;
  private final String mediaType;

  NpmPackumentVariant(String assetKind, String mediaType) {
    this.assetKind = assetKind;
    this.mediaType = mediaType;
  }

  public String assetKind() {
    return assetKind;
  }

  public String mediaType() {
    return mediaType;
  }

  public boolean abbreviated() {
    return this == INSTALL_V1;
  }

  public String cachePath(NpmPackageId packageId) {
    return this == FULL ? packageId.id() : INSTALL_V1_CACHE_PREFIX + packageId.id();
  }

  public static NpmPackumentVariant fromAccept(String accept) {
    if (accept == null || accept.isBlank()) {
      return FULL;
    }
    for (String token : accept.split(",")) {
      String media = token;
      int params = media.indexOf(';');
      if (params >= 0) {
        media = media.substring(0, params);
      }
      if (INSTALL_V1.mediaType.equals(media.trim().toLowerCase(Locale.ROOT))) {
        return INSTALL_V1;
      }
    }
    return FULL;
  }
}
