package com.github.klboke.nexusplus.protocol.maven.path;

import static com.github.klboke.nexusplus.protocol.maven.MavenConstants.DOTTED_TIMESTAMP_VERSION_FORMAT;
import static com.github.klboke.nexusplus.protocol.maven.MavenConstants.INDEX_MAIN_CHUNK_FILE_PATH;
import static com.github.klboke.nexusplus.protocol.maven.MavenConstants.INDEX_PROPERTY_FILE_PATH;
import static com.github.klboke.nexusplus.protocol.maven.MavenConstants.METADATA_DOTTED_TIMESTAMP;
import static com.github.klboke.nexusplus.protocol.maven.MavenConstants.METADATA_FILENAME;
import static com.github.klboke.nexusplus.protocol.maven.MavenConstants.SNAPSHOT_VERSION_SUFFIX;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;

/**
 * Maven 2 path parser. Ported from Nexus 3.29.2's {@code Maven2MavenPathParser}; same parsing
 * semantics, modern Java (no Joda, no Guice). Returns a {@link MavenPath} with non-null
 * coordinates iff the path is recognizable as a Maven artifact path; metadata and arbitrary paths
 * yield {@code MavenPath} with {@code coordinates() == null}.
 */
public final class MavenPathParser {
  private static final String TAR_EXT_PREFIX = ".tar";
  private static final String CPIO_EXT_PREFIX = ".cpio";
  private static final String NK_OS_EXT = ".nk.os";

  public MavenPath parsePath(String path) {
    return parsePath(path, true);
  }

  public MavenPath parsePath(String path, boolean caseSensitive) {
    Objects.requireNonNull(path);
    String stripped = path.startsWith("/") ? path.substring(1) : path;
    Coordinates coordinates = layoutedPathToCoordinates(stripped, caseSensitive);
    return new MavenPath(stripped, coordinates);
  }

  public boolean isRepositoryMetadata(MavenPath path) {
    return path.main().fileName().equals(METADATA_FILENAME);
  }

  public boolean isRepositoryIndex(MavenPath path) {
    return path.path().equals(INDEX_MAIN_CHUNK_FILE_PATH)
        || path.path().equals(INDEX_PROPERTY_FILE_PATH);
  }

  private Coordinates layoutedPathToCoordinates(String pathString, boolean caseSensitive) {
    String str = pathString;
    try {
      int vEndPos = str.lastIndexOf('/');
      if (vEndPos == -1) return null;
      int aEndPos = str.lastIndexOf('/', vEndPos - 1);
      if (aEndPos == -1) return null;
      int gEndPos = str.lastIndexOf('/', aEndPos - 1);
      if (gEndPos == -1) return null;

      String groupId = str.substring(0, gEndPos).replace('/', '.');
      String artifactId = str.substring(gEndPos + 1, aEndPos);
      String baseVersion = str.substring(aEndPos + 1, vEndPos);
      boolean snapshot = baseVersion.endsWith(SNAPSHOT_VERSION_SUFFIX);
      String fileName = str.substring(vEndPos + 1);
      str = fileName;

      StringBuilder extSuffix = new StringBuilder();
      SignatureType signatureType = null;

      for (HashType ht : HashType.values()) {
        if (str.endsWith("." + ht.ext())) {
          extSuffix.insert(0, "." + ht.ext());
          str = str.substring(0, str.length() - (ht.ext().length() + 1));
          break;
        }
      }

      for (SignatureType st : SignatureType.values()) {
        if (str.endsWith("." + st.ext())) {
          extSuffix.insert(0, "." + st.ext());
          str = str.substring(0, str.length() - (st.ext().length() + 1));
          signatureType = st;
        }
      }

      if (str.endsWith(METADATA_FILENAME)) {
        return null;
      }

      String version = baseVersion;
      Long timestamp = null;
      Integer buildNumber = null;
      String tail = null;
      if (snapshot) {
        int vSnapshotStart =
            artifactId.length() + 1 + baseVersion.length() - SNAPSHOT_VERSION_SUFFIX.length();
        version = str.substring(vSnapshotStart, vSnapshotStart + SNAPSHOT_VERSION_SUFFIX.length());

        if (SNAPSHOT_VERSION_SUFFIX.equals(version)) {
          int vTimestampStart = vSnapshotStart + version.length() + 1;
          version = baseVersion;
          tail = str.substring(artifactId.length() + baseVersion.length() + 1);

          // hokey case: SNAPSHOT-20180101.121212-style
          if (str.length() > vTimestampStart + DOTTED_TIMESTAMP_VERSION_FORMAT.length()) {
            try {
              parseDottedTimestamp(str.substring(
                  vTimestampStart, vTimestampStart + DOTTED_TIMESTAMP_VERSION_FORMAT.length()));
              version = str.substring(
                  vTimestampStart, vTimestampStart + SNAPSHOT_VERSION_SUFFIX.length());
              vSnapshotStart = vTimestampStart;
              tail = null;
            } catch (DateTimeParseException ignored) {
              // expected — most paths don't hit this branch
            }
          }
        }

        if (tail == null) {
          StringBuilder snapshotTimestampedVersion = new StringBuilder(version);
          snapshotTimestampedVersion.append(str.substring(
              vSnapshotStart + version.length(),
              vSnapshotStart + version.length() + SNAPSHOT_VERSION_SUFFIX.length() - 1));

          try {
            timestamp = parseDottedTimestamp(snapshotTimestampedVersion.toString());
          } catch (DateTimeParseException ignored) {
            // not all snapshot tails are dotted-timestamp; leave timestamp null
          }

          snapshotTimestampedVersion.append('-');
          int buildNumberPos = vSnapshotStart + snapshotTimestampedVersion.length();
          StringBuilder bnr = new StringBuilder();
          while (buildNumberPos < str.length()
              && str.charAt(buildNumberPos) >= '0' && str.charAt(buildNumberPos) <= '9') {
            snapshotTimestampedVersion.append(str.charAt(buildNumberPos));
            bnr.append(str.charAt(buildNumberPos));
            buildNumberPos++;
          }
          if (bnr.length() > 0) {
            try {
              buildNumber = Integer.parseInt(bnr.toString());
            } catch (NumberFormatException ignored) {
              // leave null
            }
          }
          tail = str.substring(vSnapshotStart + snapshotTimestampedVersion.length());
          version = baseVersion.substring(0, baseVersion.length() - SNAPSHOT_VERSION_SUFFIX.length())
              + snapshotTimestampedVersion;
        }
      } else {
        String fileNameStr = fileName;
        String artifactStr = artifactId + "-" + baseVersion;

        if (!caseSensitive) {
          fileNameStr = fileNameStr.toLowerCase(Locale.ROOT);
          artifactStr = artifactStr.toLowerCase(Locale.ROOT);
        }

        if (!fileNameStr.startsWith(artifactStr)
            || "-.".indexOf(fileNameStr.charAt(artifactStr.length())) == -1) {
          return null;
        }

        int nTailPos = artifactId.length() + baseVersion.length() + 1;
        tail = str.substring(nTailPos);
      }

      int nExtPos = getExtensionPos(tail);
      if (nExtPos == -1) {
        return null;
      }

      String ext = tail.substring(nExtPos + 1);
      String classifier = tail.charAt(0) == '-' ? tail.substring(1, nExtPos) : null;

      return new Coordinates(
          snapshot,
          groupId,
          artifactId,
          version,
          timestamp,
          buildNumber,
          baseVersion,
          classifier,
          ext + extSuffix,
          signatureType);
    } catch (StringIndexOutOfBoundsException e) {
      return null;
    }
  }

  private int getExtensionPos(String tail) {
    int nExtPos = tail.lastIndexOf('.');
    if (nExtPos == -1) {
      return -1;
    }
    String withoutExt = tail.substring(0, nExtPos);
    if (withoutExt.endsWith(TAR_EXT_PREFIX)) {
      nExtPos -= TAR_EXT_PREFIX.length();
    } else if (withoutExt.endsWith(CPIO_EXT_PREFIX)) {
      nExtPos -= CPIO_EXT_PREFIX.length();
    } else if (tail.endsWith(NK_OS_EXT)) {
      nExtPos = tail.length() - NK_OS_EXT.length();
    }
    return nExtPos;
  }

  private static long parseDottedTimestamp(String text) {
    LocalDateTime ldt = LocalDateTime.parse(text, METADATA_DOTTED_TIMESTAMP);
    return Instant.from(ldt.atOffset(ZoneOffset.UTC)).toEpochMilli();
  }
}
