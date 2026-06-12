package com.github.klboke.nexusplus.protocol.maven.path;

import java.util.Objects;

/**
 * Immutable Maven 2 path. Mirrors the semantics of Nexus's {@code MavenPath} (3.29.2) — the path
 * itself, derived file name, optional hash type (if this is a {@code .sha1/.md5/...} sibling), and
 * optional artifact coordinates. Helpers {@link #main()} and {@link #subordinateOf()} walk between
 * a main artifact and its hash/signature siblings.
 */
public final class MavenPath {
  private final String path;
  private final String fileName;
  private final HashType hashType;
  private final Coordinates coordinates;

  public MavenPath(String path, Coordinates coordinates) {
    Objects.requireNonNull(path);
    if (path.startsWith("/")) {
      throw new IllegalArgumentException("Path must not start with '/': " + path);
    }
    this.path = path;
    this.fileName = path.substring(path.lastIndexOf('/') + 1);
    HashType ht = null;
    for (HashType v : HashType.values()) {
      if (this.fileName.endsWith("." + v.ext())) {
        ht = v;
        break;
      }
    }
    this.hashType = ht;
    this.coordinates = coordinates;
  }

  public String path() {
    return path;
  }

  public String fileName() {
    return fileName;
  }

  public HashType hashType() {
    return hashType;
  }

  public Coordinates coordinates() {
    return coordinates;
  }

  public boolean isHash() {
    return hashType != null;
  }

  public boolean isSignature() {
    return coordinates != null && coordinates.signatureType() != null;
  }

  public boolean isSubordinate() {
    return isHash() || isSignature();
  }

  public boolean isPom() {
    return coordinates != null && "pom".equals(coordinates.extension());
  }

  public MavenPath main() {
    MavenPath p = this;
    while (p.isSubordinate()) {
      p = p.subordinateOf();
    }
    return p;
  }

  public MavenPath subordinateOf() {
    if (hashType != null) {
      int hashSuffixLen = hashType.ext().length() + 1;
      Coordinates main = null;
      if (coordinates != null) {
        String ext = coordinates.extension();
        main = new Coordinates(
            coordinates.snapshot(),
            coordinates.groupId(),
            coordinates.artifactId(),
            coordinates.version(),
            coordinates.timestamp(),
            coordinates.buildNumber(),
            coordinates.baseVersion(),
            coordinates.classifier(),
            ext.substring(0, ext.length() - hashSuffixLen),
            coordinates.signatureType());
      }
      return new MavenPath(path.substring(0, path.length() - hashSuffixLen), main);
    }
    if (coordinates != null && coordinates.signatureType() != null) {
      int sigLen = coordinates.signatureType().ext().length() + 1;
      String ext = coordinates.extension();
      Coordinates main = new Coordinates(
          coordinates.snapshot(),
          coordinates.groupId(),
          coordinates.artifactId(),
          coordinates.version(),
          coordinates.timestamp(),
          coordinates.buildNumber(),
          coordinates.baseVersion(),
          coordinates.classifier(),
          ext.substring(0, ext.length() - sigLen),
          null);
      return new MavenPath(path.substring(0, path.length() - sigLen), main);
    }
    return this;
  }

  public MavenPath hash(HashType hash) {
    if (hashType != null) {
      throw new IllegalStateException("Already a hash path: " + path);
    }
    Coordinates hashed = null;
    if (coordinates != null) {
      hashed = new Coordinates(
          coordinates.snapshot(),
          coordinates.groupId(),
          coordinates.artifactId(),
          coordinates.version(),
          coordinates.timestamp(),
          coordinates.buildNumber(),
          coordinates.baseVersion(),
          coordinates.classifier(),
          coordinates.extension() + "." + hash.ext(),
          coordinates.signatureType());
    }
    return new MavenPath(path + "." + hash.ext(), hashed);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MavenPath that)) return false;
    return path.equals(that.path);
  }

  @Override
  public int hashCode() {
    return path.hashCode();
  }

  @Override
  public String toString() {
    return "MavenPath{path='" + path + "', hashType=" + hashType + "}";
  }
}
