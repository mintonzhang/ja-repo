package com.github.klboke.nexusplus.protocol.maven.metadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MavenMetadataMergerTest {

  @Test
  void artifactLevelMetadataWithTopLevelVersionKeepsVersions() {
    String member = """
        <?xml version="1.0" encoding="UTF-8"?>
        <metadata>
          <groupId>org.glassfish.jaxb</groupId>
          <artifactId>jaxb-runtime</artifactId>
          <version>3.0.0</version>
          <versioning>
            <latest>4.0.8</latest>
            <release>4.0.8</release>
            <versions>
              <version>2.3.9</version>
              <version>3.0.0</version>
              <version>4.0.8</version>
            </versions>
            <lastUpdated>20260505065730</lastUpdated>
          </versioning>
        </metadata>
        """;

    byte[] merged = new MavenMetadataMerger().merge(
        List.of(member.getBytes(StandardCharsets.UTF_8)), Instant.parse("2026-05-26T00:00:00Z"));

    String xml = new String(merged, StandardCharsets.UTF_8);
    assertTrue(xml.contains("<versions>"));
    assertTrue(xml.contains("<version>2.3.9</version>"));
    assertTrue(xml.contains("<version>4.0.8</version>"));
    assertFalse(xml.contains("<snapshotVersions>"));
  }

  @Test
  void baseVersionSnapshotMetadataStillUsesSnapshotShape() {
    String member = """
        <?xml version="1.0" encoding="UTF-8"?>
        <metadata>
          <groupId>com.acme</groupId>
          <artifactId>demo</artifactId>
          <version>1.0-SNAPSHOT</version>
          <versioning>
            <snapshot>
              <timestamp>20260526.010203</timestamp>
              <buildNumber>4</buildNumber>
            </snapshot>
            <lastUpdated>20260526010203</lastUpdated>
            <snapshotVersions>
              <snapshotVersion>
                <extension>jar</extension>
                <value>1.0-20260526.010203-4</value>
                <updated>20260526010203</updated>
              </snapshotVersion>
            </snapshotVersions>
          </versioning>
        </metadata>
        """;

    byte[] merged = new MavenMetadataMerger().merge(
        List.of(member.getBytes(StandardCharsets.UTF_8)), Instant.parse("2026-05-26T00:00:00Z"));

    String xml = new String(merged, StandardCharsets.UTF_8);
    assertTrue(xml.contains("<version>1.0-SNAPSHOT</version>"));
    assertTrue(xml.contains("<snapshotVersions>"));
    assertFalse(xml.contains("<versions>"));
  }
}
