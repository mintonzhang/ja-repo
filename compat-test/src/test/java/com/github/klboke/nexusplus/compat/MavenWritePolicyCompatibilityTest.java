package com.github.klboke.nexusplus.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.klboke.nexusplus.protocol.maven.path.MavenPath;
import com.github.klboke.nexusplus.protocol.maven.path.MavenPathParser;
import com.github.klboke.nexusplus.protocol.maven.policy.LayoutPolicy;
import com.github.klboke.nexusplus.protocol.maven.policy.LayoutPolicyValidator;
import com.github.klboke.nexusplus.protocol.maven.policy.WritePolicy;
import com.github.klboke.nexusplus.protocol.maven.policy.WritePolicyGate;
import org.junit.jupiter.api.Test;

/**
 * Locks the Maven write-policy behavior copied from Nexus's MavenWritePolicySelector:
 * ALLOW_ONCE is relaxed only for repository metadata and repository index files.
 */
class MavenWritePolicyCompatibilityTest {
  private final MavenPathParser parser = new MavenPathParser();
  private final WritePolicyGate gate = new WritePolicyGate(parser);
  private final LayoutPolicyValidator layout = new LayoutPolicyValidator(parser);

  @Test
  void allowOnceLetsMetadataAndIndexBeUpdated() {
    assertTrue(gate.canWrite(WritePolicy.ALLOW_ONCE,
        path("com/acme/demo/maven-metadata.xml"), true));
    assertTrue(gate.canWrite(WritePolicy.ALLOW_ONCE,
        path(".index/nexus-maven-repository-index.properties"), true));
  }

  @Test
  void allowOnceDoesNotRelaxArtifactOrChecksumUpdates() {
    assertFalse(gate.canWrite(WritePolicy.ALLOW_ONCE,
        path("com/acme/demo/1.0.0/demo-1.0.0.jar"), true));
    assertFalse(gate.canWrite(WritePolicy.ALLOW_ONCE,
        path("com/acme/demo/1.0.0/demo-1.0.0.jar.sha1"), true));
    assertTrue(gate.canWrite(WritePolicy.ALLOW_ONCE,
        path("com/acme/demo/1.0.1/demo-1.0.1.jar.sha1"), false));
  }

  @Test
  void denyBlocksMetadataChecksumAndDelete() {
    assertFalse(gate.canWrite(WritePolicy.DENY,
        path("com/acme/demo/maven-metadata.xml"), false));
    assertFalse(gate.canWrite(WritePolicy.DENY,
        path("com/acme/demo/1.0.0/demo-1.0.0.jar.sha1"), false));
    assertFalse(gate.canDelete(WritePolicy.DENY,
        path("com/acme/demo/maven-metadata.xml")));
  }

  @Test
  void strictLayoutRejectsIncompleteTimestampedSnapshotsLikeNexus() {
    assertFalse(layout.isLayoutCompliant(LayoutPolicy.STRICT,
        path("com/acme/demo/1.0-SNAPSHOT/demo-1.0-20260522.123456.jar")));
    assertFalse(layout.isLayoutCompliant(LayoutPolicy.STRICT,
        path("com/acme/demo/1.0-SNAPSHOT/demo-1.0-20260522.123456.jar.sha1")));
    assertTrue(layout.isLayoutCompliant(LayoutPolicy.STRICT,
        path("com/acme/demo/1.0-SNAPSHOT/demo-1.0-20260522.123456-1.jar")));
    assertTrue(layout.isLayoutCompliant(LayoutPolicy.STRICT,
        path("com/acme/demo/1.0-SNAPSHOT/demo-1.0-SNAPSHOT.jar")));
  }

  private MavenPath path(String path) {
    return parser.parsePath(path);
  }
}
