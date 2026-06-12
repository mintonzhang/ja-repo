package com.github.klboke.nexusplus.protocol.maven.metadata;

import static com.github.klboke.nexusplus.protocol.maven.MavenConstants.METADATA_DOTLESS_TIMESTAMP;
import static com.github.klboke.nexusplus.protocol.maven.MavenConstants.METADATA_DOTTED_TIMESTAMP;
import static com.github.klboke.nexusplus.protocol.maven.MavenConstants.SNAPSHOT_VERSION_SUFFIX;

import com.github.klboke.nexusplus.protocol.maven.metadata.Maven2Metadata.Plugin;
import com.github.klboke.nexusplus.protocol.maven.metadata.Maven2Metadata.Snapshot;
import com.github.klboke.nexusplus.protocol.maven.metadata.MavenMetadataXml.Parsed;
import com.github.klboke.nexusplus.protocol.maven.metadata.MavenMetadataXml.ParsedPlugin;
import com.github.klboke.nexusplus.protocol.maven.metadata.MavenMetadataXml.ParsedSnapshot;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges Maven {@code maven-metadata.xml} documents using the same observable rules Nexus applies
 * for Maven group repositories.
 */
public final class MavenMetadataMerger {

  public byte[] merge(List<byte[]> metadataBlobs, Instant now) {
    MergeState state = new MergeState(now);
    for (byte[] blob : metadataBlobs) {
      try {
        state.add(MavenMetadataXml.read(blob));
      } catch (IOException ignored) {
        // Nexus drops unreadable member metadata from a group merge.
      }
    }
    return state.toXml();
  }

  private static final class MergeState {
    private final Instant now;
    private String groupId;
    private String artifactId;
    private String version;
    private boolean baseVersionLevel;
    private boolean pluginLevel;
    private Instant lastUpdated = Instant.EPOCH;
    private final List<String> versions = new ArrayList<>();
    private final Map<String, Plugin> plugins = new LinkedHashMap<>();
    private Long snapshotTimestamp;
    private int snapshotBuildNumber;
    private final Map<String, Snapshot> snapshotVersions = new LinkedHashMap<>();

    private MergeState(Instant now) {
      this.now = now == null ? Instant.now() : now;
    }

    private void add(Parsed parsed) {
      if (parsed == null) return;
      if (parsed.groupId != null && !parsed.groupId.isBlank()) groupId = parsed.groupId;
      if (parsed.artifactId != null && !parsed.artifactId.isBlank()) artifactId = parsed.artifactId;
      boolean artifactLevel = !parsed.versions.isEmpty();
      if (parsed.version != null && !parsed.version.isBlank()) {
        version = parsed.version;
        if (!artifactLevel) {
          baseVersionLevel = true;
        }
      }
      for (String v : parsed.versions) {
        if (v != null && !v.equals("null") && !versions.contains(v)) {
          versions.add(v);
        }
      }
      Instant updated = parseDotlessTimestamp(parsed.lastUpdated);
      if (updated != null && updated.isAfter(lastUpdated)) {
        lastUpdated = updated;
      }
      Long parsedSnapshotTimestamp = parseDottedTimestampMillis(parsed.snapshotTimestamp);
      if (parsedSnapshotTimestamp != null
          && (snapshotTimestamp == null || parsedSnapshotTimestamp > snapshotTimestamp)) {
        snapshotTimestamp = parsedSnapshotTimestamp;
        snapshotBuildNumber = parsed.snapshotBuildNumber;
      }
      for (ParsedSnapshot snapshot : parsed.snapshotVersions) {
        addSnapshot(snapshot);
      }
      for (ParsedPlugin plugin : parsed.plugins) {
        addPlugin(plugin);
      }
    }

    private byte[] toXml() {
      Instant effectiveLastUpdated = lastUpdated.equals(Instant.EPOCH) ? now : lastUpdated;
      Maven2Metadata metadata;
      if (baseVersionLevel && versions.isEmpty()) {
        metadata = Maven2Metadata.baseVersionLevel(
            effectiveLastUpdated,
            groupId,
            artifactId,
            version,
            snapshotTimestamp,
            snapshotBuildNumber,
            new ArrayList<>(snapshotVersions.values()));
      } else if (pluginLevel && versions.isEmpty()) {
        List<Plugin> sortedPlugins = new ArrayList<>(plugins.values());
        sortedPlugins.sort(Comparator.comparing(Plugin::artifactId, Comparator.nullsFirst(String::compareTo)));
        metadata = Maven2Metadata.groupLevel(effectiveLastUpdated, sortedPlugins);
      } else {
        versions.sort(MavenVersionComparator.INSTANCE);
        String latest = versions.isEmpty() ? null : versions.get(versions.size() - 1);
        String release = null;
        for (int i = versions.size() - 1; i >= 0; i--) {
          String candidate = versions.get(i);
          if (!candidate.endsWith(SNAPSHOT_VERSION_SUFFIX)) {
            release = candidate;
            break;
          }
        }
        metadata = Maven2Metadata.artifactLevel(
            effectiveLastUpdated, groupId, artifactId, latest, release, versions);
      }
      return MavenMetadataXml.write(metadata);
    }

    private void addSnapshot(ParsedSnapshot parsed) {
      if (parsed == null || parsed.extension == null || parsed.value == null) return;
      String key = (parsed.classifier == null ? "" : parsed.classifier) + ":" + parsed.extension;
      Snapshot existing = snapshotVersions.get(key);
      if (existing == null
          || MavenVersionComparator.INSTANCE.compare(parsed.value, existing.version()) > 0) {
        Instant updated = parseDotlessTimestamp(parsed.updated);
        snapshotVersions.put(key, new Snapshot(
            updated == null ? now : updated,
            parsed.extension,
            emptyToNull(parsed.classifier),
            parsed.value));
      }
    }

    private void addPlugin(ParsedPlugin parsed) {
      if (parsed == null) return;
      pluginLevel = true;
      String key = (parsed.artifactId == null ? "" : parsed.artifactId)
          + ":" + (parsed.prefix == null ? "" : parsed.prefix);
      plugins.put(key, new Plugin(parsed.artifactId, parsed.prefix, parsed.name));
    }
  }

  private static Instant parseDotlessTimestamp(String text) {
    if (text == null || text.isBlank()) return null;
    try {
      return LocalDateTime.parse(text.trim(), METADATA_DOTLESS_TIMESTAMP).toInstant(ZoneOffset.UTC);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static Long parseDottedTimestampMillis(String text) {
    if (text == null || text.isBlank()) return null;
    try {
      return LocalDateTime.parse(text.trim(), METADATA_DOTTED_TIMESTAMP).toInstant(ZoneOffset.UTC).toEpochMilli();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
