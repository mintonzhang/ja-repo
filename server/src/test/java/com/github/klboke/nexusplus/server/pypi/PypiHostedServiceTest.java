package com.github.klboke.nexusplus.server.pypi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.klboke.nexusplus.core.BlobObjectMetadata;
import com.github.klboke.nexusplus.core.BlobReference;
import com.github.klboke.nexusplus.core.BlobStorage;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryType;
import com.github.klboke.nexusplus.persistence.mysql.dao.AssetDao;
import com.github.klboke.nexusplus.persistence.mysql.dao.BrowseNodeDao;
import com.github.klboke.nexusplus.persistence.mysql.dao.ComponentDao;
import com.github.klboke.nexusplus.persistence.mysql.dao.RepositoryIndexRebuildDao;
import com.github.klboke.nexusplus.persistence.mysql.model.AssetRecord;
import com.github.klboke.nexusplus.server.cache.AssetMetadataCache;
import com.github.klboke.nexusplus.server.maven.BlobStorageRegistry;
import com.github.klboke.nexusplus.server.maven.RepositoryRuntime;
import com.github.klboke.nexusplus.server.support.InMemorySharedCache;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PypiHostedServiceTest {
  @Test
  void uploadEnqueuesProjectAndRootIndexRebuildsWithoutSynchronousIndexWrite() throws Exception {
    RecordingIndexRebuildDao indexRebuildDao = new RecordingIndexRebuildDao();
    RecordingWriter writer = new RecordingWriter();
    EmptyAssetDao assetDao = new EmptyAssetDao();
    AssetMetadataCache cache = new AssetMetadataCache(new InMemorySharedCache(), false, 0, 0);
    PypiHostedService service = new PypiHostedService(
        assetDao,
        new EmptyComponentDao(),
        indexRebuildDao,
        new FixedBlobStorageRegistry(),
        writer,
        new PypiAssetReader(assetDao, new FixedBlobStorageRegistry()),
        cache,
        0);

    service.upload(
        runtime(),
        Map.of(
            ":action", "file_upload",
            "name", "Demo_Pkg",
            "version", "1.0.0"),
        new MockMultipartFile(
            "content",
            "demo_pkg-1.0.0.tar.gz",
            "application/gzip",
            "payload".getBytes()),
        null,
        "admin",
        "127.0.0.1");

    assertEquals(1, writer.packageWrites);
    assertEquals(0, writer.indexWrites);
    assertEquals(List.of(
        "10:" + RepositoryIndexRebuildDao.PYPI_PROJECT + ":demo-pkg",
        "10:" + RepositoryIndexRebuildDao.PYPI_ROOT + ":"),
        indexRebuildDao.enqueues);
  }

  private static RepositoryRuntime runtime() {
    return new RepositoryRuntime(
        10,
        "pypi-hosted",
        RepositoryFormat.PYPI,
        RepositoryType.HOSTED,
        "pypi-hosted",
        true,
        1L,
        "ALLOW",
        null,
        null,
        true,
        null,
        null,
        null,
        null,
        null,
        List.of());
  }

  private static class EmptyAssetDao extends AssetDao {
    EmptyAssetDao() {
      super(null, null);
    }

    @Override
    public Optional<AssetRecord> findAssetByPath(long repositoryId, String path) {
      return Optional.empty();
    }

    @Override
    public List<PypiProjectIndexRow> listPypiProjectIndexRows(long repositoryId, String normalizedName) {
      fail("PyPI upload must not synchronously rebuild project simple index");
      return List.of();
    }
  }

  private static class EmptyComponentDao extends ComponentDao {
    EmptyComponentDao() {
      super(null, null);
    }
  }

  private static class RecordingIndexRebuildDao extends RepositoryIndexRebuildDao {
    final List<String> enqueues = new ArrayList<>();

    RecordingIndexRebuildDao() {
      super(null);
    }

    @Override
    public void enqueue(long repositoryId, String indexKind) {
      enqueues.add(repositoryId + ":" + indexKind + ":");
    }

    @Override
    public void enqueue(long repositoryId, String indexKind, String scopeKey) {
      enqueues.add(repositoryId + ":" + indexKind + ":" + scopeKey);
    }
  }

  private static class RecordingWriter extends PypiAssetWriter {
    int packageWrites;
    int indexWrites;

    RecordingWriter() {
      super(null, null, null, new AssetMetadataCache(new InMemorySharedCache(), false, 0, 0),
          null, null);
    }

    @Override
    Stored write(
        RepositoryRuntime runtime,
        BlobStorage storage,
        long blobStoreId,
        String path,
        InputStream body,
        String contentType,
        String kind,
        PackageCoordinate coordinate,
        Map<String, Object> assetAttributes,
        Map<String, String> extraBlobAttributes,
        String createdBy,
        String createdByIp) {
      packageWrites++;
      assertEquals("packages/demo-pkg/1.0.0/demo_pkg-1.0.0.tar.gz", path);
      assertEquals("package", kind);
      return null;
    }

    @Override
    Stored writeBytes(
        RepositoryRuntime runtime,
        BlobStorage storage,
        long blobStoreId,
        String path,
        byte[] body,
        String contentType,
        String kind,
        PackageCoordinate coordinate,
        Map<String, Object> assetAttributes,
        String createdBy,
        String createdByIp) {
      indexWrites++;
      fail("PyPI upload must enqueue index rebuilds instead of writing index blobs synchronously");
      return null;
    }
  }

  private static class FixedBlobStorageRegistry extends BlobStorageRegistry {
    FixedBlobStorageRegistry() {
      super(null, null, null, null, 0L);
    }

    @Override
    public BlobStorage forBlobStoreId(long blobStoreId) {
      return new NoopBlobStorage();
    }
  }

  private static class NoopBlobStorage implements BlobStorage {
    @Override
    public BlobReference put(String repository, String logicalPath, InputStream content, long size, String sha256) {
      return new BlobReference("test", "unused", sha256, size);
    }

    @Override
    public Optional<InputStream> get(BlobReference reference) {
      return Optional.empty();
    }

    @Override
    public boolean exists(BlobReference reference) {
      return false;
    }

    @Override
    public Optional<BlobObjectMetadata> stat(BlobReference reference) {
      return Optional.empty();
    }

    @Override
    public void delete(BlobReference reference) {
    }
  }
}
