package com.github.klboke.nexusplus.protocol.maven.path;

import static com.github.klboke.nexusplus.protocol.maven.MavenConstants.INDEX_MAIN_CHUNK_FILE_PATH;
import static com.github.klboke.nexusplus.protocol.maven.MavenConstants.INDEX_PROPERTY_FILE_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/**
 * Cribbed from Nexus 3.29.2's {@code Maven2MavenPathParserTest}. Exercises the same fixtures so
 * any divergence from upstream parsing semantics fails here.
 */
class MavenPathParserTest {
  private final MavenPathParser parser = new MavenPathParser();

  private long parseTimestamp(String ts) throws Exception {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd.HHmmss");
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    return sdf.parse(ts).getTime();
  }

  @Test
  void artifactSnapshotTimestamped() throws Exception {
    MavenPath p = parser.parsePath(
        "/org/jruby/jruby/1.0RC1-SNAPSHOT/jruby-1.0RC1-20070504.160758-25-javadoc.jar");
    assertNotNull(p.coordinates());
    assertEquals("org.jruby", p.coordinates().groupId());
    assertEquals("jruby", p.coordinates().artifactId());
    assertEquals("1.0RC1-20070504.160758-25", p.coordinates().version());
    assertEquals("1.0RC1-SNAPSHOT", p.coordinates().baseVersion());
    assertEquals("javadoc", p.coordinates().classifier());
    assertEquals("jar", p.coordinates().extension());
    assertEquals(parseTimestamp("20070504.160758"), p.coordinates().timestamp());
    assertEquals(25, p.coordinates().buildNumber());
    assertNull(p.hashType());
  }

  @Test
  void artifactReleaseClassifierHash() {
    MavenPath p = parser.parsePath(
        "/com/sun/xml/ws/jaxws-local-transport/2.1.3/jaxws-local-transport-2.1.3.pom.md5");
    assertEquals(HashType.MD5, p.hashType());
    assertNotNull(p.coordinates());
    assertEquals("com.sun.xml.ws", p.coordinates().groupId());
    assertEquals("jaxws-local-transport", p.coordinates().artifactId());
    assertEquals("2.1.3", p.coordinates().version());
    assertEquals("pom.md5", p.coordinates().extension());
  }

  @Test
  void artifactRelease() {
    MavenPath p = parser.parsePath("/junit/junit/3.8/junit-3.8.jar");
    assertNotNull(p.coordinates());
    assertEquals("junit", p.coordinates().groupId());
    assertEquals("junit", p.coordinates().artifactId());
    assertEquals("3.8", p.coordinates().version());
    assertEquals("3.8", p.coordinates().baseVersion());
    assertEquals("jar", p.coordinates().extension());
    assertNull(p.coordinates().classifier());
    assertFalse(p.coordinates().snapshot());
  }

  @Test
  void artifactNonHyphenSnapshot() {
    MavenPath p = parser.parsePath("/foo1/foo1/0.0.1SNAPSHOT/foo1-0.0.1SNAPSHOT.pom");
    assertNotNull(p.coordinates());
    assertEquals("0.0.1SNAPSHOT", p.coordinates().version());
    assertEquals("0.0.1SNAPSHOT", p.coordinates().baseVersion());
    assertEquals("pom", p.coordinates().extension());
  }

  @Test
  void artifactSignature() {
    MavenPath p = parser.parsePath(
        "/org/apache/maven/artifact/maven-artifact/3.0-SNAPSHOT/maven-artifact-3.0-20080411.005221-75.pom.asc");
    assertNotNull(p.coordinates());
    assertEquals(SignatureType.GPG, p.coordinates().signatureType());
    assertEquals("pom.asc", p.coordinates().extension());
  }

  @Test
  void artifactSignatureHash() {
    MavenPath p = parser.parsePath(
        "/org/apache/maven/artifact/maven-artifact/3.0-SNAPSHOT/maven-artifact-3.0-20080411.005221-75.pom.asc.sha1");
    assertEquals(HashType.SHA1, p.hashType());
    assertEquals(SignatureType.GPG, p.coordinates().signatureType());
    assertEquals("pom.asc.sha1", p.coordinates().extension());
  }

  @Test
  void metadata() {
    MavenPath p = parser.parsePath("/org/jruby/jruby/1.0/maven-metadata.xml");
    assertNull(p.coordinates());
    assertTrue(parser.isRepositoryMetadata(p));
    assertFalse(parser.isRepositoryIndex(p));
  }

  @Test
  void metadataHash() {
    MavenPath p = parser.parsePath(
        "/org/codehaus/plexus/plexus-container-default/maven-metadata.xml.md5");
    assertNull(p.coordinates());
    assertEquals(HashType.MD5, p.hashType());
    assertTrue(parser.isRepositoryMetadata(p));
  }

  @Test
  void index() {
    MavenPath props = parser.parsePath(INDEX_PROPERTY_FILE_PATH);
    assertTrue(parser.isRepositoryIndex(props));
    MavenPath chunk = parser.parsePath(INDEX_MAIN_CHUNK_FILE_PATH);
    assertTrue(parser.isRepositoryIndex(chunk));
  }

  @Test
  void garbage() {
    assertNull(parser.parsePath("/").coordinates());
    assertNull(parser.parsePath("/some/stupid/path").coordinates());
    assertNull(parser.parsePath("/something/that/looks/like-an-artifact.pom").coordinates());
  }

  @Test
  void tarGz() {
    MavenPath p = parser.parsePath(
        "/org/sonatype/nexus/nexus-webapp/1.0.0-beta-5/nexus-webapp-1.0.0-beta-5-bundle.tar.gz");
    assertNotNull(p.coordinates());
    assertEquals("tar.gz", p.coordinates().extension());
    assertEquals("bundle", p.coordinates().classifier());
  }

  @Test
  void nkOs() {
    MavenPath p = parser.parsePath(
        "/org/sonatype/nexus/tools/nexus-migration-app/1.0.0-beta-6-SNAPSHOT/nexus-migration-app-1.0.0-beta-6-20080809.181715-2-cli.nk.os");
    assertEquals("nk.os", p.coordinates().extension());
  }

  @Test
  void subordinateAndMain() {
    MavenPath sha1 = parser.parsePath("/org/jruby/jruby/1.0/jruby-1.0-javadoc.jar.sha1");
    assertTrue(sha1.isHash());
    MavenPath main = sha1.main();
    assertFalse(main.isHash());
    assertEquals("jar", main.coordinates().extension());
    assertEquals("org/jruby/jruby/1.0/jruby-1.0-javadoc.jar", main.path());
  }

  @Test
  void hashRebuild() {
    MavenPath jar = parser.parsePath("/org/jruby/jruby/1.0/jruby-1.0.jar");
    MavenPath sha256 = jar.hash(HashType.SHA256);
    assertEquals("org/jruby/jruby/1.0/jruby-1.0.jar.sha256", sha256.path());
    assertEquals(HashType.SHA256, sha256.hashType());
    assertEquals("jar.sha256", sha256.coordinates().extension());
  }

  @Test
  void generatedChecksumPayloadMatchesNexusStringPayload() {
    byte[] payload = ChecksumPayload.format("0123456789abcdef");
    assertEquals("0123456789abcdef", new String(payload, StandardCharsets.UTF_8));
    assertEquals(16, payload.length);
  }
}
