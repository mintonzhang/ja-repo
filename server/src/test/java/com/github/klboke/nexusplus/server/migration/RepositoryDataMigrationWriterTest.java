package com.github.klboke.nexusplus.server.migration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.klboke.nexusplus.protocol.maven.path.HashType;
import com.github.klboke.nexusplus.protocol.maven.path.MavenPath;
import com.github.klboke.nexusplus.protocol.maven.path.MavenPathParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RepositoryDataMigrationWriterTest {
  private static final byte[] SAMPLE = "nexus-plus migration checksum\n".getBytes(StandardCharsets.UTF_8);
  private static final MavenPathParser MAVEN_PATH_PARSER = new MavenPathParser();

  @Test
  void generatedMavenChecksumPayloadsMatchNexusUploadHandlerSemantics() {
    String md5 = "f6ca101e3d90cd2d04ba14c3cea2a21a";
    String sha1 = "1047373161507ecd96088b733b47df2c30de75b7";
    String sha256 = "917ec70870ad551619f0a47cff5c0fb6c223f6c8b29788b41ea17570ee93b87a";
    String sha512 = "45aa591f744593d410631f46c10d09cb5b00acce0dde21ecfae53206beeb099549283e904d476d49e92d2bea7c04f753eb80514e40a2ef83d9e5b8e494291e20";

    assertEquals(md5, digest(HashType.MD5));
    assertEquals(sha1, digest(HashType.SHA1));
    assertEquals(sha256, digest(HashType.SHA256));
    assertEquals(sha512, digest(HashType.SHA512));

    MavenPath mainPath = MAVEN_PATH_PARSER.parsePath("com/acme/app/1.0/app-1.0.jar");
    List<RepositoryDataMigrationWriter.GeneratedMavenChecksum> checksums =
        RepositoryDataMigrationWriter.generatedMavenChecksums(mainPath, md5, sha1, sha256, sha512);

    assertEquals(4, checksums.size());
    assertChecksum(checksums.get(0), "com/acme/app/1.0/app-1.0.jar.md5", md5);
    assertChecksum(checksums.get(1), "com/acme/app/1.0/app-1.0.jar.sha1", sha1);
    assertChecksum(checksums.get(2), "com/acme/app/1.0/app-1.0.jar.sha256", sha256);
    assertChecksum(checksums.get(3), "com/acme/app/1.0/app-1.0.jar.sha512", sha512);
  }

  @Test
  void reusableBlobAttributesAlreadyContainingSha512DoNotNeedUpdate() {
    RepositoryDataMigrationWriter.Digests digests = new RepositoryDataMigrationWriter.Digests(
        "md5", "sha1", "sha256", "sha512", 123L);

    assertNull(RepositoryDataMigrationWriter.mergeReusableBlobAttributes(
        Map.of("sha512", "sha512", "sourceAssetId", "#12:1"),
        digests));
  }

  @Test
  void reusableBlobAttributesOnlyBackfillMissingSha512() {
    RepositoryDataMigrationWriter.Digests digests = new RepositoryDataMigrationWriter.Digests(
        "md5", "sha1", "sha256", "sha512", 123L);

    Map<String, Object> attributes = RepositoryDataMigrationWriter.mergeReusableBlobAttributes(
        Map.of("sourceAssetId", "#12:1"),
        digests);

    assertEquals("sha512", attributes.get("sha512"));
    assertEquals("#12:1", attributes.get("sourceAssetId"));
    assertFalse(attributes.containsKey("sourceBlobRef"));
    assertFalse(attributes.containsKey("sourceMetadata"));
  }

  private static void assertChecksum(
      RepositoryDataMigrationWriter.GeneratedMavenChecksum checksum,
      String expectedPath,
      String expectedHex) {
    assertEquals(expectedPath, checksum.path().path());
    assertArrayEquals(expectedHex.getBytes(StandardCharsets.UTF_8), checksum.payload());
    assertEquals(expectedHex, new String(checksum.payload(), StandardCharsets.UTF_8));
  }

  private static String digest(HashType hashType) {
    try {
      MessageDigest digest = MessageDigest.getInstance(hashType.javaAlgorithm());
      return HexFormat.of().formatHex(digest.digest(SAMPLE));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("Missing digest algorithm " + hashType.javaAlgorithm(), e);
    }
  }
}
