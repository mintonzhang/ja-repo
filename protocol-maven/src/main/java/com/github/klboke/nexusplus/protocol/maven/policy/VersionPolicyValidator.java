package com.github.klboke.nexusplus.protocol.maven.policy;

import com.github.klboke.nexusplus.protocol.maven.path.Coordinates;
import com.github.klboke.nexusplus.protocol.maven.path.MavenPath;

public final class VersionPolicyValidator {

  /**
   * Returns {@code true} if the given artifact path is allowed under this policy. Metadata files
   * (coordinates == null) are always permitted at the version-policy layer; layout policy already
   * filters arbitrary junk. Hash/signature siblings are validated against their main coordinates.
   */
  public boolean isVersionCompliant(VersionPolicy policy, MavenPath path) {
    if (policy == VersionPolicy.MIXED) return true;
    Coordinates coords = path.main().coordinates();
    if (coords == null) return true;
    return policy == VersionPolicy.SNAPSHOT ? coords.snapshot() : !coords.snapshot();
  }
}
