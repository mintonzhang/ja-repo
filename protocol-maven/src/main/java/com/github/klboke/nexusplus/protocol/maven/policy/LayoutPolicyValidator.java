package com.github.klboke.nexusplus.protocol.maven.policy;

import com.github.klboke.nexusplus.protocol.maven.path.MavenPath;
import com.github.klboke.nexusplus.protocol.maven.path.MavenPathParser;

public final class LayoutPolicyValidator {
  private final MavenPathParser parser;

  public LayoutPolicyValidator(MavenPathParser parser) {
    this.parser = parser;
  }

  /**
   * STRICT rejects anything that doesn't parse to Maven coordinates AND isn't a known metadata or
   * index file. Timestamped snapshots also need both timestamp and build number, matching Nexus's
   * MavenContentHandler validation. PERMISSIVE allows everything. Hash/signature sibling status is
   * taken from the main path, so {@code foo-1.0.jar.sha1} validates the same way as
   * {@code foo-1.0.jar}.
   */
  public boolean isLayoutCompliant(LayoutPolicy policy, MavenPath path) {
    if (policy == LayoutPolicy.PERMISSIVE) return true;
    MavenPath main = path.main();
    if (main.coordinates() != null) {
      return !isInvalidTimestampedSnapshot(main);
    }
    return parser.isRepositoryMetadata(path) || parser.isRepositoryIndex(path);
  }

  private boolean isInvalidTimestampedSnapshot(MavenPath path) {
    var coordinates = path.coordinates();
    return coordinates.snapshot()
        && !coordinates.version().equals(coordinates.baseVersion())
        && (coordinates.timestamp() == null || coordinates.buildNumber() == null);
  }
}
