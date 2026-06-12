package com.github.klboke.nexusplus.server.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klboke.nexusplus.cache.InMemorySharedCache;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryType;
import com.github.klboke.nexusplus.persistence.mysql.dao.RepositoryDao;
import com.github.klboke.nexusplus.persistence.mysql.model.RepositoryRecord;
import com.github.klboke.nexusplus.server.catalog.CatalogCacheBroadcaster;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryRuntimeRegistryTest {
  @Test
  void resolveTruncatesRecursiveGroupMembers() {
    FakeRepositoryDao dao = new FakeRepositoryDao();
    RepositoryRecord root = repo(1, "root", RepositoryType.GROUP);
    RepositoryRecord nested = repo(2, "nested", RepositoryType.GROUP);
    RepositoryRecord hosted = repo(3, "hosted", RepositoryType.HOSTED);
    dao.add(root, List.of(nested));
    dao.add(nested, List.of(root, hosted));
    dao.add(hosted, List.of());

    RepositoryRuntime runtime = new RepositoryRuntimeRegistry(dao, 0).resolve("root").orElseThrow();

    RepositoryRuntime nestedRuntime = runtime.members().get(0);
    assertEquals("nested", nestedRuntime.name());
    assertEquals("root", nestedRuntime.members().get(0).name());
    assertTrue(nestedRuntime.members().get(0).members().isEmpty());
    assertEquals("hosted", nestedRuntime.members().get(1).name());
  }

  @Test
  void catalogBroadcastFlushesCachedRuntimes() {
    FakeRepositoryDao dao = new FakeRepositoryDao();
    dao.add(repo(1, "hosted", RepositoryType.HOSTED), List.of());
    InMemoryBroadcaster broadcaster = new InMemoryBroadcaster();
    // Match Spring Boot's ObjectMapper, which ignores RepositoryRuntime's is-getter properties
    // (hosted/proxy/group) on read-back instead of failing.
    ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    RepositoryRuntimeRegistry registry = new RepositoryRuntimeRegistry(
        dao, new InMemorySharedCache(mapper, 1000, null), mapper, broadcaster, 300);
    registry.subscribeToCatalogBroadcast();

    registry.resolve("hosted").orElseThrow();
    registry.resolve("hosted").orElseThrow();
    assertEquals(1, dao.findByNameCalls); // second read served from the cached runtime

    broadcaster.publishRefresh("repository");

    registry.resolve("hosted").orElseThrow();
    assertEquals(2, dao.findByNameCalls); // broadcast flushed the cache → reloaded from MySQL
  }

  private static RepositoryRecord repo(long id, String name, RepositoryType type) {
    return new RepositoryRecord(
        id,
        name,
        RepositoryFormat.MAVEN2,
        type,
        type == RepositoryType.GROUP ? "maven2-group" : "maven2-hosted",
        true,
        1L,
        null,
        null,
        "MIXED",
        "PERMISSIVE",
        "ALLOW",
        true,
        Map.of());
  }

  private static class FakeRepositoryDao extends RepositoryDao {
    private final Map<Long, RepositoryRecord> byId = new HashMap<>();
    private final Map<String, RepositoryRecord> byName = new HashMap<>();
    private final Map<Long, List<RepositoryRecord>> members = new HashMap<>();
    private int findByNameCalls;

    FakeRepositoryDao() {
      super(null, null);
    }

    void add(RepositoryRecord record, List<RepositoryRecord> memberRecords) {
      byId.put(record.id(), record);
      byName.put(record.name(), record);
      members.put(record.id(), memberRecords);
    }

    @Override
    public Optional<RepositoryRecord> findById(long id) {
      return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<RepositoryRecord> findByName(String name) {
      findByNameCalls++;
      return Optional.ofNullable(byName.get(name));
    }

    @Override
    public List<RepositoryRecord> listMembers(long groupRepositoryId) {
      return members.getOrDefault(groupRepositoryId, List.of());
    }
  }

  private static final class InMemoryBroadcaster implements CatalogCacheBroadcaster {
    private final Map<String, List<Runnable>> listeners = new LinkedHashMap<>();

    @Override
    public void subscribe(String catalogName, Runnable refreshListener) {
      listeners.computeIfAbsent(catalogName, ignored -> new ArrayList<>()).add(refreshListener);
    }

    @Override
    public void publishRefresh(String catalogName) {
      List.copyOf(listeners.getOrDefault(catalogName, List.of())).forEach(Runnable::run);
    }
  }
}
