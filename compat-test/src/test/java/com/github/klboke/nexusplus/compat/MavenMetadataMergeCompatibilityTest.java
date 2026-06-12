package com.github.klboke.nexusplus.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.klboke.nexusplus.protocol.maven.metadata.MavenMetadataMerger;
import com.github.klboke.nexusplus.protocol.maven.metadata.MavenMetadataXml;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MavenMetadataMergeCompatibilityTest {
  private static final Instant FIXED_NOW = Instant.parse("2026-01-02T03:04:05Z");
  private final MavenMetadataMerger merger = new MavenMetadataMerger();

  @Test
  void artifactVersionsAreSortedAndLatestReleaseAreRecomputedLikeNexus() throws IOException {
    MavenMetadataXml.Parsed parsed = merge("""
        <metadata>
          <groupId>com.acme</groupId>
          <artifactId>demo</artifactId>
          <versioning>
            <latest>wrong</latest>
            <release>wrong</release>
            <versions>
              <version>2.0.0</version>
              <version>1.1-SNAPSHOT</version>
            </versions>
            <lastUpdated>20260101000000</lastUpdated>
          </versioning>
        </metadata>
        """, """
        <metadata>
          <groupId>com.acme</groupId>
          <artifactId>demo</artifactId>
          <versioning>
            <versions>
              <version>1.0-foo</version>
              <version>1.0-beta</version>
              <version>1.2</version>
            </versions>
            <lastUpdated>20260102000000</lastUpdated>
          </versioning>
        </metadata>
        """);

    assertEquals(List.of("1.0-beta", "1.0-foo", "1.1-SNAPSHOT", "1.2", "2.0.0"),
        parsed.versions);
    assertEquals("2.0.0", parsed.latest);
    assertEquals("2.0.0", parsed.release);
    assertEquals("20260102000000", parsed.lastUpdated);
  }

  @Test
  void artifactReleaseIsAbsentWhenOnlySnapshotsExist() throws IOException {
    MavenMetadataXml.Parsed parsed = merge("""
        <metadata>
          <groupId>com.acme</groupId>
          <artifactId>demo</artifactId>
          <versioning>
            <versions>
              <version>2.0-SNAPSHOT</version>
              <version>1.0-SNAPSHOT</version>
            </versions>
          </versioning>
        </metadata>
        """);

    assertEquals(List.of("1.0-SNAPSHOT", "2.0-SNAPSHOT"), parsed.versions);
    assertEquals("2.0-SNAPSHOT", parsed.latest);
    assertNull(parsed.release);
  }

  @Test
  void snapshotVersionsAreDeduplicatedByExtensionAndClassifierUsingVersionOrder() throws IOException {
    MavenMetadataXml.Parsed parsed = merge("""
        <metadata>
          <groupId>com.acme</groupId>
          <artifactId>demo</artifactId>
          <version>1.0-SNAPSHOT</version>
          <versioning>
            <snapshot>
              <timestamp>20260101.120000</timestamp>
              <buildNumber>1</buildNumber>
            </snapshot>
            <snapshotVersions>
              <snapshotVersion>
                <extension>jar</extension>
                <value>1.0-20260101.120000-1</value>
                <updated>20260101120000</updated>
              </snapshotVersion>
            </snapshotVersions>
            <lastUpdated>20260101120000</lastUpdated>
          </versioning>
        </metadata>
        """, """
        <metadata>
          <groupId>com.acme</groupId>
          <artifactId>demo</artifactId>
          <version>1.0-SNAPSHOT</version>
          <versioning>
            <snapshot>
              <timestamp>20260101.120001</timestamp>
              <buildNumber>2</buildNumber>
            </snapshot>
            <snapshotVersions>
              <snapshotVersion>
                <extension>jar</extension>
                <value>1.0-20260101.120001-2</value>
                <updated>20260101120001</updated>
              </snapshotVersion>
              <snapshotVersion>
                <classifier>sources</classifier>
                <extension>jar</extension>
                <value>1.0-20260101.120001-2</value>
                <updated>20260101120001</updated>
              </snapshotVersion>
            </snapshotVersions>
            <lastUpdated>20260101120001</lastUpdated>
          </versioning>
        </metadata>
        """);

    assertEquals("20260101.120001", parsed.snapshotTimestamp);
    assertEquals(2, parsed.snapshotBuildNumber);
    assertEquals(2, parsed.snapshotVersions.size());
    assertEquals("1.0-20260101.120001-2", parsed.snapshotVersions.get(0).value);
    assertNull(parsed.snapshotVersions.get(0).classifier);
    assertEquals("sources", parsed.snapshotVersions.get(1).classifier);
  }

  @Test
  void groupPluginsAreSortedByArtifactId() throws IOException {
    MavenMetadataXml.Parsed parsed = merge("""
        <metadata>
          <plugins>
            <plugin><name>Zed</name><prefix>z</prefix><artifactId>z-plugin</artifactId></plugin>
            <plugin><name>Ace</name><prefix>a</prefix><artifactId>a-plugin</artifactId></plugin>
          </plugins>
        </metadata>
        """);

    assertEquals("a-plugin", parsed.plugins.get(0).artifactId);
    assertEquals("z-plugin", parsed.plugins.get(1).artifactId);
  }

  private MavenMetadataXml.Parsed merge(String... xml) throws IOException {
    List<byte[]> blobs = java.util.Arrays.stream(xml)
        .map(s -> s.getBytes(StandardCharsets.UTF_8))
        .toList();
    return MavenMetadataXml.read(merger.merge(blobs, FIXED_NOW));
  }
}
