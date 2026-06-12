package com.github.klboke.nexusplus.migration.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryType;
import com.github.klboke.nexusplus.migration.nexus.NexusApiMigrationService.ConfigMigrationCounts;
import com.github.klboke.nexusplus.migration.nexus.NexusApiMigrationService.NexusMigrationRequest;
import com.github.klboke.nexusplus.migration.nexus.NexusApiMigrationService.NexusMigrationTargetBlobStore;
import com.github.klboke.nexusplus.migration.nexus.NexusRestClient.NexusInventory;
import com.github.klboke.nexusplus.migration.nexus.NexusRestClient.RepositoryDocument;
import com.github.klboke.nexusplus.migration.nexus.security.NexusSecurityExport;
import com.github.klboke.nexusplus.persistence.mysql.dao.BlobStoreDao;
import com.github.klboke.nexusplus.persistence.mysql.dao.RepositoryDao;
import com.github.klboke.nexusplus.persistence.mysql.model.BlobStoreRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.RepositoryRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class NexusApiMigrationServiceTest {

  @Test
  void migratesSupportedRepositoriesAndKeepsSourceGroupMembers() {
    FakeBlobStoreDao blobStores = new FakeBlobStoreDao();
    FakeRepositoryDao repositories = new FakeRepositoryDao();
    NexusApiMigrationService service = service(blobStores, repositories);

    ConfigMigrationCounts counts = service.migrateConfig(new NexusInventory(
        List.of(Map.of("name", "default")),
        List.of(
            repository("maven-releases", "maven2", "hosted", Map.of(
                "storage", storage("default"),
                "maven", Map.of("versionPolicy", "release", "layoutPolicy", "strict"))),
            repository("npm-hosted", "npm", "hosted", Map.of("storage", storage("default"))),
            repository("maven-central", "maven2", "proxy", Map.of(
                "storage", storage("default"),
                "proxy", Map.of("remoteUrl", "https://repo1.maven.org/maven2/"))),
            repository("maven-public", "maven2", "group", Map.of(
                "storage", storage("default"),
                "group", Map.of("memberNames", List.of("maven-releases", "maven-central"))))),
        NexusSecurityExport.empty(),
        List.of()), request("https://old-nexus.example/nexus/"));

    RepositoryRecord mavenHosted = repositories.required("maven-releases");
    assertEquals(4, counts.repositories());
    assertEquals(0, counts.unsupportedRepositories());
    assertEquals(1, counts.proxyRepositories());
    assertEquals(1, counts.groupRepositories());
    assertEquals(RepositoryFormat.MAVEN2, mavenHosted.format());
    assertEquals(RepositoryType.HOSTED, mavenHosted.type());
    assertEquals("maven2-hosted", mavenHosted.recipeName());
    assertEquals("RELEASE", mavenHosted.versionPolicy());
    assertEquals("STRICT", mavenHosted.layoutPolicy());
    assertFalse(repositories.findByName("maven-releases-source-proxy").isPresent());
    assertFalse(repositories.findByName("npm-hosted-source-proxy").isPresent());
    assertEquals(
        List.of("maven-releases", "maven-central"),
        repositories.memberNames("maven-public"));
  }

  @Test
  void unsupportedHostedRepositoryDoesNotCreateFallbackProxy() {
    FakeBlobStoreDao blobStores = new FakeBlobStoreDao();
    FakeRepositoryDao repositories = new FakeRepositoryDao();
    NexusApiMigrationService service = service(blobStores, repositories);

    ConfigMigrationCounts counts = service.migrateConfig(new NexusInventory(
        List.of(Map.of("name", "default")),
        List.of(
            repository("go-hosted", "go", "hosted", Map.of("storage", storage("default"))),
            repository("go-public", "go", "group", Map.of(
                "storage", storage("default"),
                "group", Map.of("memberNames", List.of("go-hosted"))))),
        NexusSecurityExport.empty(),
        List.of()), request("https://old-nexus.example"));

    assertEquals(1, counts.unsupportedRepositories());
    assertEquals(1, counts.repositories());
    assertEquals(1, counts.groupRepositories());
    assertTrue(repositories.findByName("go-hosted").isEmpty());
    assertTrue(repositories.findByName("go-public").isPresent());
    assertFalse(repositories.findByName("go-hosted-source-proxy").isPresent());
    assertEquals(List.of(), repositories.memberNames("go-public"));
  }

  private static NexusApiMigrationService service(BlobStoreDao blobStores, RepositoryDao repositories) {
    return new NexusApiMigrationService(
        new ObjectMapper(),
        blobStores,
        repositories,
        null,
        null,
        null);
  }

  private static NexusMigrationRequest request(String sourceBaseUrl) {
    return new NexusMigrationRequest(
        sourceBaseUrl,
        "admin",
        "secret",
        "3.29.2-02",
        false,
        new NexusMigrationTargetBlobStore("default", "s3", null, null, null, "", Map.of()));
  }

  private static RepositoryDocument repository(
      String name,
      String format,
      String type,
      Map<String, Object> detail) {
    LinkedHashMap<String, Object> source = new LinkedHashMap<>();
    source.put("name", name);
    source.put("format", format);
    source.put("type", type);
    source.put("online", true);
    source.putAll(detail);
    return new RepositoryDocument(
        Map.of("name", name, "format", format, "type", type),
        source);
  }

  private static Map<String, Object> storage(String blobStoreName) {
    return Map.of(
        "blobStoreName", blobStoreName,
        "strictContentTypeValidation", true,
        "writePolicy", "allow_once");
  }

  private static final class FakeBlobStoreDao extends BlobStoreDao {
    private final AtomicLong ids = new AtomicLong(100);
    private final Map<String, BlobStoreRecord> records = new LinkedHashMap<>();

    private FakeBlobStoreDao() {
      super(null, null);
    }

    @Override
    public BlobStoreRecord upsertByName(BlobStoreRecord record) {
      Long id = Optional.ofNullable(records.get(record.name()))
          .map(BlobStoreRecord::id)
          .orElseGet(ids::incrementAndGet);
      BlobStoreRecord stored = new BlobStoreRecord(
          id,
          record.name(),
          record.type(),
          record.endpoint(),
          record.region(),
          record.bucket(),
          record.prefix(),
          record.attributes());
      records.put(stored.name(), stored);
      return stored;
    }

    @Override
    public Optional<BlobStoreRecord> findByName(String name) {
      return Optional.ofNullable(records.get(name));
    }
  }

  private static final class FakeRepositoryDao extends RepositoryDao {
    private final AtomicLong ids = new AtomicLong(200);
    private final Map<String, RepositoryRecord> records = new LinkedHashMap<>();
    private final Map<Long, List<Long>> members = new LinkedHashMap<>();

    private FakeRepositoryDao() {
      super(null, null);
    }

    @Override
    public RepositoryRecord upsertByName(RepositoryRecord record) {
      Long id = Optional.ofNullable(records.get(record.name()))
          .map(RepositoryRecord::id)
          .orElseGet(ids::incrementAndGet);
      RepositoryRecord stored = new RepositoryRecord(
          id,
          record.name(),
          record.format(),
          record.type(),
          record.recipeName(),
          record.online(),
          record.blobStoreId(),
          record.routingRuleId(),
          record.proxyRemoteUrl(),
          record.versionPolicy(),
          record.layoutPolicy(),
          record.writePolicy(),
          record.strictContentTypeValidation(),
          record.attributes());
      records.put(stored.name(), stored);
      return stored;
    }

    @Override
    public Optional<RepositoryRecord> findByName(String name) {
      return Optional.ofNullable(records.get(name));
    }

    @Override
    public void replaceMembers(long groupRepositoryId, List<Long> memberRepositoryIds) {
      members.put(groupRepositoryId, List.copyOf(memberRepositoryIds));
    }

    @Override
    public List<RepositoryRecord> listMembers(long groupRepositoryId) {
      return members.getOrDefault(groupRepositoryId, List.of()).stream()
          .map(this::requiredById)
          .toList();
    }

    private RepositoryRecord required(String name) {
      return findByName(name).orElseThrow();
    }

    private List<String> memberNames(String groupName) {
      return listMembers(required(groupName).id()).stream()
          .map(RepositoryRecord::name)
          .toList();
    }

    private RepositoryRecord requiredById(Long id) {
      return records.values().stream()
          .filter(record -> record.id().equals(id))
          .findFirst()
          .orElseThrow();
    }
  }
}
