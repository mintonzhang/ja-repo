package com.github.klboke.nexusplus.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.klboke.nexusplus.protocol.npm.NpmMetadata;
import com.github.klboke.nexusplus.protocol.npm.NpmPackageId;
import com.github.klboke.nexusplus.protocol.npm.NpmPath;
import com.github.klboke.nexusplus.protocol.npm.NpmPathParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NpmProtocolCompatibilityTest {
  private final NpmPathParser parser = new NpmPathParser();

  @Test
  void parsesNexusNpmPackageAndTarballRoutes() {
    assertEquals(NpmPath.Kind.REPOSITORY_ROOT, parser.parse("").kind());

    NpmPath unscoped = parser.parse("left-pad");
    assertEquals(NpmPath.Kind.PACKAGE_ROOT, unscoped.kind());
    assertEquals("left-pad", unscoped.packageId().id());

    NpmPath scoped = parser.parse("@tap/demo/-/demo-1.2.3.tgz");
    assertEquals(NpmPath.Kind.TARBALL, scoped.kind());
    assertEquals("@tap/demo", scoped.packageId().id());
    assertEquals("demo-1.2.3.tgz", scoped.tarballName());

    NpmPath encodedScoped = parser.parse("%40tap%2Fdemo");
    assertEquals(NpmPath.Kind.PACKAGE_ROOT, encodedScoped.kind());
    assertEquals("@tap/demo", encodedScoped.packageId().id());

    NpmPath plusName = parser.parse("pkg+name");
    assertEquals(NpmPath.Kind.PACKAGE_ROOT, plusName.kind());
    assertEquals("pkg+name", plusName.packageId().id());
  }

  @Test
  void rejectsInvalidPackagePathsWithoutThrowing() {
    NpmPath invalidScoped = parser.parse("@tap");
    assertEquals(NpmPath.Kind.UNKNOWN, invalidScoped.kind());

    NpmPath invalidDistTagPackage = parser.parse("-/package/@tap/dist-tags");
    assertEquals(NpmPath.Kind.UNKNOWN, invalidDistTagPackage.kind());
  }

  @Test
  void parsesNexusDistTagRoutes() {
    NpmPath tags = parser.parse("-/package/@tap/demo/dist-tags");
    assertEquals(NpmPath.Kind.DIST_TAGS, tags.kind());
    assertEquals("@tap/demo", tags.packageId().id());

    NpmPath tag = parser.parse("-/package/left-pad/dist-tags/beta");
    assertEquals(NpmPath.Kind.DIST_TAG, tag.kind());
    assertEquals("left-pad", tag.packageId().id());
    assertEquals("beta", tag.tag());
  }

  @Test
  void parsesNpmAuditAdvisoriesBulkRoute() {
    NpmPath advisories = parser.parse("-/npm/v1/security/advisories/bulk");

    assertEquals(NpmPath.Kind.ADVISORIES_BULK, advisories.kind());
  }

  @Test
  void parsesNexusNpmAuditRoutes() {
    NpmPath audit = parser.parse("-/npm/v1/security/audits");
    NpmPath quick = parser.parse("-/npm/v1/security/audits/quick");

    assertEquals(NpmPath.Kind.AUDIT, audit.kind());
    assertEquals(NpmPath.Kind.AUDIT_QUICK, quick.kind());
  }

  @Test
  void mergesPackageRootsWithLatestVersionAndNoRevision() {
    Map<String, Object> one = packageRoot("demo", "1.0.0");
    Map<String, Object> two = packageRoot("demo", "2.0.0");
    one.put("_rev", "1");
    two.put("_rev", "2");

    Map<String, Object> merged = NpmMetadata.merge(List.of(one, two));

    assertEquals("2.0.0", NpmMetadata.distTags(merged).get("latest"));
    assertEquals(2, NpmMetadata.versions(merged).size());
    assertNull(merged.get("_rev"));
  }

  @Test
  void shrinksPackageRootForLegacySearchIndex() {
    Map<String, Object> root = packageRoot("demo", "1.0.0");

    Map<String, Object> shrunk = NpmMetadata.shrinkForSearch(root);

    assertEquals("latest", NpmMetadata.versions(shrunk).get("1.0.0"));
    assertTrue(NpmMetadata.versions(root).get("1.0.0") instanceof Map);
  }

  @Test
  void rewritesTarballUrlsToRepositoryBase() {
    Map<String, Object> root = packageRoot("@tap/demo", "1.0.0");

    NpmMetadata.rewriteTarballUrls(root, NpmPackageId.parse("@tap/demo"), "http://localhost/repository/npm");

    Map<String, Object> version = version(root, "1.0.0");
    Map<String, Object> dist = child(version, "dist");
    assertEquals("http://localhost/repository/npm/@tap/demo/-/demo-1.0.0.tgz", dist.get("tarball"));
  }

  private static Map<String, Object> packageRoot(String name, String version) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("name", name);
    NpmMetadata.distTags(root).put("latest", version);
    Map<String, Object> versionMap = new LinkedHashMap<>();
    versionMap.put("name", name);
    versionMap.put("version", version);
    Map<String, Object> dist = new LinkedHashMap<>();
    dist.put("tarball", "https://registry.example/" + name + "/-/" + NpmPackageId.parse(name).name() + "-" + version + ".tgz");
    versionMap.put("dist", dist);
    NpmMetadata.versions(root).put(version, versionMap);
    return root;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> version(Map<String, Object> root, String version) {
    return (Map<String, Object>) NpmMetadata.versions(root).get(version);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> child(Map<String, Object> root, String key) {
    return (Map<String, Object>) root.get(key);
  }
}
