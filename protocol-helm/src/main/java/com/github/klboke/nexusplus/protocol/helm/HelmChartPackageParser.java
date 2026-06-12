package com.github.klboke.nexusplus.protocol.helm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class HelmChartPackageParser {
  private static final String CHART_YAML = "Chart.yaml";
  private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

  public HelmChartMetadata parse(InputStream input) throws IOException {
    byte[] chartYaml = readChartYaml(input);
    Object loaded = yaml.load(new ByteArrayInputStream(chartYaml));
    if (!(loaded instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("Chart.yaml is not a YAML mapping");
    }
    HelmChartMetadata metadata = HelmChartMetadata.fromYamlMap(castMap(map));
    metadata.requireNameAndVersion();
    return metadata;
  }

  private byte[] readChartYaml(InputStream input) throws IOException {
    try (GzipCompressorInputStream gzip = new GzipCompressorInputStream(input);
         TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
      ArchiveEntry entry;
      while ((entry = tar.getNextEntry()) != null) {
        if (!entry.isDirectory() && entry.getName().endsWith(CHART_YAML)) {
          return readCurrentEntry(tar);
        }
      }
    }
    throw new IllegalArgumentException(CHART_YAML + " not found in Helm chart package");
  }

  private static byte[] readCurrentEntry(TarArchiveInputStream tar) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int n;
    while ((n = tar.read(buffer)) > 0) {
      out.write(buffer, 0, n);
    }
    return out.toByteArray();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }
}
