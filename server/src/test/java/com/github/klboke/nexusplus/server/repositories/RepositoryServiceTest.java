package com.github.klboke.nexusplus.server.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryType;
import com.github.klboke.nexusplus.persistence.mysql.dao.BlobStoreDao;
import com.github.klboke.nexusplus.persistence.mysql.dao.RepositoryDao;
import com.github.klboke.nexusplus.persistence.mysql.model.BlobStoreRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.RepositoryRecord;
import com.github.klboke.nexusplus.server.maven.RepositoryRuntimeRegistry;
import com.github.klboke.nexusplus.server.cache.NexusLikeCacheController;
import com.github.klboke.nexusplus.server.repositories.RepositoryCommands.UpdateCommand;
import com.github.klboke.nexusplus.server.support.InMemoryVersionWatermark;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryServiceTest {

  @Test
  void updateRejectsBlobStoreChange() {
    StubRepositoryDao repositories = new StubRepositoryDao(repository(1L));
    RepositoryService service = service(repositories);

    RepositoryValidationException thrown = assertThrows(RepositoryValidationException.class,
        () -> service.update("maven-releases",
            new UpdateCommand(true, "v2", null, null, null, null, null)));

    assertEquals("blobStoreName cannot be changed after repository creation", thrown.getMessage());
    assertNull(repositories.updated);
  }

  @Test
  void updateAllowsSameBlobStoreNameForExistingClients() {
    StubRepositoryDao repositories = new StubRepositoryDao(repository(1L));
    RepositoryService service = service(repositories);

    RepositoryView updated = service.update("maven-releases",
        new UpdateCommand(false, "default", null, null, null, null, null));

    assertEquals("default", updated.blobStoreName());
    assertEquals(1L, repositories.updated.blobStoreId());
    assertEquals(false, repositories.updated.online());
  }

  @Test
  void updateKeepsBlobStoreWhenPayloadOmitsIt() {
    StubRepositoryDao repositories = new StubRepositoryDao(repository(1L));
    RepositoryService service = service(repositories);

    RepositoryView updated = service.update("maven-releases",
        new UpdateCommand(false, null, null, null, null, null, null));

    assertEquals("default", updated.blobStoreName());
    assertEquals(1L, repositories.updated.blobStoreId());
  }

  @Test
  void updateBumpsRepositoryCacheTokens() {
    StubRepositoryDao repositories = new StubRepositoryDao(repository(1L));
    InMemoryVersionWatermark watermark = new InMemoryVersionWatermark();
    RepositoryService service = new RepositoryService(
        repositories,
        new StubBlobStoreDao(),
        null,
        new RepositoryRuntimeRegistry(repositories, 0),
        new NexusLikeCacheController(watermark, 60),
        "/repository");

    service.update("maven-releases",
        new UpdateCommand(false, null, null, null, null, null, null));

    assertEquals(1, watermark.current("repo:10:CONTENT"));
    assertEquals(1, watermark.current("repo:10:METADATA"));
  }

  private static RepositoryService service(StubRepositoryDao repositories) {
    return new RepositoryService(
        repositories,
        new StubBlobStoreDao(),
        null,
        new RepositoryRuntimeRegistry(repositories, 0),
        "/repository");
  }

  private static RepositoryRecord repository(long blobStoreId) {
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("recipe", "maven2-hosted");
    return new RepositoryRecord(
        10L,
        "maven-releases",
        RepositoryFormat.MAVEN2,
        RepositoryType.HOSTED,
        "maven2-hosted",
        true,
        blobStoreId,
        null,
        null,
        "RELEASE",
        "STRICT",
        "ALLOW_ONCE",
        true,
        attributes);
  }

  private static final class StubRepositoryDao extends RepositoryDao {
    private RepositoryRecord repository;
    private RepositoryRecord updated;

    private StubRepositoryDao(RepositoryRecord repository) {
      super(null, null);
      this.repository = repository;
    }

    @Override
    public Optional<RepositoryRecord> findByName(String name) {
      return repository.name().equals(name) ? Optional.of(repository) : Optional.empty();
    }

    @Override
    public List<RepositoryRecord> listGroupsContaining(long memberRepositoryId) {
      return List.of();
    }

    @Override
    public void update(RepositoryRecord record) {
      updated = record;
      repository = record;
    }
  }

  private static final class StubBlobStoreDao extends BlobStoreDao {
    private final List<BlobStoreRecord> stores = List.of(
        blobStore(1L, "default"),
        blobStore(2L, "v2"));

    private StubBlobStoreDao() {
      super(null, null);
    }

    @Override
    public Optional<BlobStoreRecord> findByName(String name) {
      return stores.stream()
          .filter(store -> store.name().equals(name))
          .findFirst();
    }

    @Override
    public List<BlobStoreRecord> list() {
      return stores;
    }
  }

  private static BlobStoreRecord blobStore(long id, String name) {
    return new BlobStoreRecord(id, name, "s3", null, null, "bucket", null, Map.of());
  }
}
