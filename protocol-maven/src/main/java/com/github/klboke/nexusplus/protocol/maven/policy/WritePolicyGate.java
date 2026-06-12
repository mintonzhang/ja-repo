package com.github.klboke.nexusplus.protocol.maven.policy;

import com.github.klboke.nexusplus.protocol.maven.path.MavenPath;
import com.github.klboke.nexusplus.protocol.maven.path.MavenPathParser;

public final class WritePolicyGate {
  private final MavenPathParser parser;

  public WritePolicyGate(MavenPathParser parser) {
    this.parser = parser;
  }

  /**
   * Mirrors Nexus's Maven write-policy selector: {@code ALLOW_ONCE} is relaxed to
   * {@code ALLOW} only for repository metadata and index files. Artifact checksum/signature
   * sidecars keep the repository's configured write policy.
   */
  public boolean canWrite(WritePolicy policy, MavenPath path, boolean assetExists) {
    WritePolicy effective = effectivePolicy(policy, path);
    return assetExists ? effective.checkUpdateAllowed() : effective.checkCreateAllowed();
  }

  public boolean canDelete(WritePolicy policy, MavenPath path) {
    return policy.checkDeleteAllowed();
  }

  private WritePolicy effectivePolicy(WritePolicy policy, MavenPath path) {
    if (policy == WritePolicy.ALLOW_ONCE
        && (parser.isRepositoryMetadata(path) || parser.isRepositoryIndex(path))) {
      return WritePolicy.ALLOW;
    }
    return policy;
  }
}
