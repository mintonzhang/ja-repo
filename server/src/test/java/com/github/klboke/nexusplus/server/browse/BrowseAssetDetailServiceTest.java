package com.github.klboke.nexusplus.server.browse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klboke.nexusplus.core.BlobObjectMetadata;
import com.github.klboke.nexusplus.core.BlobReference;
import com.github.klboke.nexusplus.core.BlobStorage;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryType;
import com.github.klboke.nexusplus.persistence.mysql.dao.AssetDao;
import com.github.klboke.nexusplus.persistence.mysql.dao.RepositoryDao;
import com.github.klboke.nexusplus.persistence.mysql.model.AssetBlobRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.AssetRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.RepositoryRecord;
import com.github.klboke.nexusplus.persistence.mysql.support.HashColumns;
import com.github.klboke.nexusplus.server.maven.BlobStorageRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;

class BrowseAssetDetailServiceTest {

  @Test
  void npmTarballWithoutRootDocumentFallsBackToTarballPackageJson() throws IOException {
    RepositoryRecord repository = repository(1L, "npm-hosted", RepositoryFormat.NPM, RepositoryType.HOSTED);
    String tarballPath = "demo/-/demo-1.0.0.tgz";
    byte[] tarballBytes = tarball("""
        {"name":"demo","version":"1.0.0","license":"MIT","keywords":["browse","fallback"]}
        """);
    AssetRecord tarball = asset(10L, repository.id(), 100L, tarballPath);
    AssetBlobRecord blob = blob(100L, tarballBytes.length);
    StubAssetDao assets = new StubAssetDao(
        Map.of(key(repository.id(), tarballPath), tarball),
        Map.of(blob.id(), blob));
    StubBlobStorage storage = new StubBlobStorage(tarballBytes);
    BrowseAssetDetailService service = new BrowseAssetDetailService(
        new StubRepositoryDao(),
        assets,
        new StubBlobStorageRegistry(storage),
        new ObjectMapper());

    BrowseAssetDetailService.BrowseAssetDetail detail = service.detail(repository, tarballPath, null);

    assertEquals("TARBALL", detail.npm().get("asset_kind"));
    assertEquals("MIT", detail.npm().get("license"));
    assertEquals("browse fallback", detail.npm().get("keywords"));
    assertEquals("demo", detail.npm().get("name"));
    assertEquals("1.0.0", detail.npm().get("version"));
    assertEquals(1, storage.gets);
    assertEquals(List.of(key(repository.id(), tarballPath), key(repository.id(), "demo")), assets.pathLookups);
  }

  @Test
  void pypiPublicPathDownloadUrlUsesPackagesProtocolPath() {
    RepositoryRecord repository = repository(1L, "pypi-hosted", RepositoryFormat.PYPI, RepositoryType.HOSTED);
    String publicPath = "demo/1.0.0/demo-1.0.0-py3-none-any.whl";
    String storagePath = "packages/" + publicPath;
    AssetRecord wheel = asset(10L, repository.id(), 100L, storagePath, RepositoryFormat.PYPI);
    AssetBlobRecord blob = blob(100L, 952L);
    StubAssetDao assets = new StubAssetDao(
        Map.of(key(repository.id(), storagePath), wheel),
        Map.of(blob.id(), blob));
    BrowseAssetDetailService service = new BrowseAssetDetailService(
        new StubRepositoryDao(),
        assets,
        new StubBlobStorageRegistry(new StubBlobStorage(new byte[0])),
        new ObjectMapper());

    BrowseAssetDetailService.BrowseAssetDetail detail = service.detail(repository, publicPath, null);

    assertEquals("/repository/pypi-hosted/packages/demo/1.0.0/demo-1.0.0-py3-none-any.whl", detail.downloadUrl());
    assertEquals(List.of(key(repository.id(), storagePath)), assets.pathLookups);
  }

  private static RepositoryRecord repository(
      long id,
      String name,
      RepositoryFormat format,
      RepositoryType type) {
    return new RepositoryRecord(
        id,
        name,
        format,
        type,
        format.name().toLowerCase(Locale.ROOT) + "-" + type.name().toLowerCase(Locale.ROOT),
        true,
        1L,
        null,
        null,
        null,
        null,
        null,
        true,
        Map.of());
  }

  private static AssetRecord asset(long id, long repositoryId, long blobId, String path) {
    return asset(id, repositoryId, blobId, path, RepositoryFormat.NPM);
  }

  private static AssetRecord asset(
      long id,
      long repositoryId,
      long blobId,
      String path,
      RepositoryFormat format) {
    return new AssetRecord(
        id,
        repositoryId,
        null,
        blobId,
        format,
        path,
        HashColumns.pathHash(path),
        path.substring(path.lastIndexOf('/') + 1),
        "tarball",
        "application/x-tgz",
        1024L,
        null,
        Instant.parse("2026-05-27T00:00:00Z"),
        Map.of("version", "1.0.0"));
  }

  private static AssetBlobRecord blob(long id, long size) {
    return new AssetBlobRecord(
        id,
        1L,
        "s3://bucket/npm/demo/-/demo-1.0.0.tgz",
        HashColumns.pathHash("s3://bucket/npm/demo/-/demo-1.0.0.tgz"),
        "npm/demo/-/demo-1.0.0.tgz",
        HashColumns.pathHash("npm/demo/-/demo-1.0.0.tgz"),
        "sha1",
        "sha256",
        "md5",
        size,
        "application/x-tgz",
        "alice",
        "127.0.0.1",
        Instant.parse("2026-05-27T00:00:00Z"),
        Instant.parse("2026-05-27T00:00:00Z"),
        Map.of());
  }

  private static String key(long repositoryId, String path) {
    return repositoryId + "|" + path;
  }

  private static byte[] tarball(String packageJson) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(bytes);
         TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
      byte[] packageJsonBytes = packageJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      TarArchiveEntry entry = new TarArchiveEntry("package/package.json");
      entry.setSize(packageJsonBytes.length);
      tar.putArchiveEntry(entry);
      tar.write(packageJsonBytes);
      tar.closeArchiveEntry();
    }
    return bytes.toByteArray();
  }

  private static class StubRepositoryDao extends RepositoryDao {
    private StubRepositoryDao() {
      super(null, null);
    }
  }

  private static class StubAssetDao extends AssetDao {
    private final Map<String, AssetRecord> assetsByPath;
    private final Map<Long, AssetBlobRecord> blobsById;
    private final ArrayList<String> pathLookups = new ArrayList<>();

    private StubAssetDao(Map<String, AssetRecord> assetsByPath, Map<Long, AssetBlobRecord> blobsById) {
      super(null, null);
      this.assetsByPath = assetsByPath;
      this.blobsById = blobsById;
    }

    @Override
    public Optional<AssetRecord> findAssetByPath(long repositoryId, String path) {
      pathLookups.add(key(repositoryId, path));
      return Optional.ofNullable(assetsByPath.get(key(repositoryId, path)));
    }

    @Override
    public Optional<AssetBlobRecord> findBlobById(long assetBlobId) {
      return Optional.ofNullable(blobsById.get(assetBlobId));
    }
  }

  private static class StubBlobStorageRegistry extends BlobStorageRegistry {
    private final BlobStorage storage;

    private StubBlobStorageRegistry(BlobStorage storage) {
      super(null, null, null, null, 0L);
      this.storage = storage;
    }

    @Override
    public BlobStorage forBlobStoreId(long blobStoreId) {
      return storage;
    }
  }

  private static class StubBlobStorage implements BlobStorage {
    private final byte[] content;
    private int gets;

    private StubBlobStorage(byte[] content) {
      this.content = content;
    }

    @Override
    public BlobReference put(String repository, String logicalPath, InputStream content, long size, String sha256) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<InputStream> get(BlobReference reference) {
      gets++;
      return Optional.of(new ByteArrayInputStream(content));
    }

    @Override
    public boolean exists(BlobReference reference) {
      return true;
    }

    @Override
    public Optional<BlobObjectMetadata> stat(BlobReference reference) {
      return Optional.empty();
    }

    @Override
    public void delete(BlobReference reference) {
      throw new UnsupportedOperationException();
    }
  }
}
