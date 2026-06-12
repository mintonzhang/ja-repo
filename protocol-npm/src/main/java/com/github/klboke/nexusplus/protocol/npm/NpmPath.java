package com.github.klboke.nexusplus.protocol.npm;

public record NpmPath(
    Kind kind,
    String rawPath,
    NpmPackageId packageId,
    String packageVersion,
    String tarballName,
    String tag,
    String revision) {

  public enum Kind {
    REPOSITORY_ROOT,
    PING,
    WHOAMI,
    SEARCH_V1,
    SEARCH_INDEX,
    AUDIT,
    AUDIT_QUICK,
    ADVISORIES_BULK,
    PACKAGE_ROOT,
    PACKAGE_VERSION,
    TARBALL,
    DIST_TAGS,
    DIST_TAG,
    UNKNOWN
  }

  public boolean isPackageMetadata() {
    return kind == Kind.PACKAGE_ROOT || kind == Kind.PACKAGE_VERSION;
  }

  public boolean isTarball() {
    return kind == Kind.TARBALL;
  }

  public String assetPath() {
    if (kind == Kind.TARBALL) {
      return packageId.tarballPath(tarballName);
    }
    if (packageId != null) {
      return packageId.id();
    }
    return rawPath;
  }
}
