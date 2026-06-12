package com.github.klboke.nexusplus.protocol.helm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

class HelmProtocolTest {
  @Test
  void parsesChartYamlFromPackage() throws Exception {
    HelmChartMetadata metadata = new HelmChartPackageParser()
        .parse(new ByteArrayInputStream(chartPackage("demo", "1.2.3")));

    assertEquals("demo", metadata.name());
    assertEquals("1.2.3", metadata.version());
    assertEquals("v2", metadata.apiVersion());
  }

  @Test
  void rewritesProxyIndexUrlsToNexusLocalChartNames() {
    byte[] upstream = """
        apiVersion: v1
        entries:
          demo:
            - apiVersion: v2
              name: demo
              version: 1.2.3
              urls:
                - charts/demo-1.2.3.tgz
        generated: "2026-05-20T00:00:00Z"
        """.getBytes(StandardCharsets.UTF_8);

    HelmIndex.RewriteResult rewritten = HelmIndex.rewriteProxyIndex(
        upstream, "https://example.test/helm");

    List<HelmIndex.Entry> entries = HelmIndex.entries(rewritten.body());
    assertEquals(1, entries.size());
    assertEquals(List.of("demo-1.2.3.tgz"), entries.get(0).urls());
    assertEquals("https://example.test/helm/charts/demo-1.2.3.tgz",
        rewritten.remoteUrlsByLocalPath().get("demo-1.2.3.tgz"));
    assertTrue(new String(rewritten.body(), StandardCharsets.UTF_8).contains("demo-1.2.3.tgz"));
  }

  @Test
  void streamsHostedIndexAsValidYaml() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    HelmIndex.writeHosted(List.of(
            new HelmIndex.ChartRecord(
                "demo",
                "1.2.3",
                "v2",
                "Demo chart",
                "1.2",
                null,
                Instant.parse("2026-05-20T01:02:03Z"),
                "sha256-demo",
                List.of("demo-1.2.3.tgz"),
                List.of("https://example.test/source"),
                List.of(Map.of("name", "ops", "email", "ops@example.test")))),
        Instant.parse("2026-05-20T00:00:00Z"),
        out);

    byte[] body = out.toByteArray();
    List<HelmIndex.Entry> entries = HelmIndex.entries(body);
    String yaml = new String(body, StandardCharsets.UTF_8);

    assertEquals(1, entries.size());
    assertEquals("demo", entries.get(0).name());
    assertEquals("1.2.3", entries.get(0).version());
    assertEquals(List.of("demo-1.2.3.tgz"), entries.get(0).urls());
    assertTrue(yaml.contains("generated: \"2026-05-20T00:00:00Z\""));
    assertTrue(yaml.contains("digest: \"sha256-demo\""));
  }

  @Test
  void streamsEmptyHostedIndexAsValidYaml() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    HelmIndex.writeHosted(List.of(), Instant.parse("2026-05-20T00:00:00Z"), out);

    assertEquals(List.of(), HelmIndex.entries(out.toByteArray()));
    assertTrue(new String(out.toByteArray(), StandardCharsets.UTF_8).contains("entries: {}"));
  }

  private static byte[] chartPackage(String name, String version) throws Exception {
    ByteArrayOutputStream gzipBytes = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(gzipBytes);
         TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      putEntry(tar, name + "/Chart.yaml", """
          apiVersion: v2
          name: %s
          version: %s
          """.formatted(name, version));
      putEntry(tar, name + "/templates/configmap.yaml", """
          apiVersion: v1
          kind: ConfigMap
          metadata:
            name: demo
          """);
    }
    return gzipBytes.toByteArray();
  }

  private static void putEntry(TarArchiveOutputStream tar, String name, String body) throws Exception {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    TarArchiveEntry entry = new TarArchiveEntry(name);
    entry.setSize(bytes.length);
    tar.putArchiveEntry(entry);
    tar.write(bytes);
    tar.closeArchiveEntry();
  }
}
