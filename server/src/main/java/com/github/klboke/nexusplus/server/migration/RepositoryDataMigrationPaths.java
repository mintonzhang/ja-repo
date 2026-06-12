package com.github.klboke.nexusplus.server.migration;

import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.protocol.maven.path.MavenPath;
import java.util.Locale;

final class RepositoryDataMigrationPaths {
  private static final String[] MAVEN_CHECKSUM_SUFFIXES = {
      ".md5", ".sha1", ".sha256", ".sha512"
  };

  private RepositoryDataMigrationPaths() {
  }

  static boolean shouldDiscoverAsset(RepositoryFormat format, String path) {
    return path != null
        && !path.isBlank()
        && !(format == RepositoryFormat.MAVEN2 && isMavenChecksumPath(path));
  }

  static boolean shouldGenerateMavenChecksumSiblings(MavenPath path) {
    return path != null
        && !path.isHash()
        && !isMavenChecksumPath(path.path());
  }

  static boolean isMavenChecksumPath(String path) {
    if (path == null || path.isBlank()) {
      return false;
    }
    String normalized = path.toLowerCase(Locale.ROOT);
    for (String suffix : MAVEN_CHECKSUM_SUFFIXES) {
      if (normalized.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }
}
