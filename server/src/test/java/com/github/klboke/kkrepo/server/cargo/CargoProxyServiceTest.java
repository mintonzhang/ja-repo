package com.github.klboke.kkrepo.server.cargo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klboke.kkrepo.core.RepositoryFormat;
import com.github.klboke.kkrepo.core.RepositoryType;
import com.github.klboke.kkrepo.persistence.mysql.dao.AssetDao;
import com.github.klboke.kkrepo.persistence.mysql.dao.ComponentDao;
import com.github.klboke.kkrepo.persistence.mysql.dao.ProxyStateDao;
import com.github.klboke.kkrepo.protocol.cargo.CargoPath;
import com.github.klboke.kkrepo.server.cache.AssetMetadataCache;
import com.github.klboke.kkrepo.server.maven.BlobStorageRegistry;
import com.github.klboke.kkrepo.server.maven.HttpRemoteFetcher;
import com.github.klboke.kkrepo.server.maven.MavenResponse;
import com.github.klboke.kkrepo.server.maven.ProxyNegativeCache;
import com.github.klboke.kkrepo.server.maven.RepositoryRuntime;
import com.github.klboke.kkrepo.server.support.InMemorySharedCache;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class CargoProxyServiceTest {
  private static final String REMOTE = "https://index.crates.io/";
  private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
  };

  @Test
  void sparseUnavailableStatusIsStoredInNegativeCache() {
    AssetDao assetDao = mock(AssetDao.class);
    when(assetDao.findAssetByPath(anyLong(), anyString())).thenReturn(Optional.empty());
    ProxyStateDao proxyState = mock(ProxyStateDao.class);
    when(proxyState.isBlocked(anyLong(), org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(false);
    CountingFetcher fetcher = new CountingFetcher(new HttpRemoteFetcher.Result(
        451, Map.of(), InputStream.nullInputStream()));
    CargoProxyService service = new CargoProxyService(
        assetDao,
        mock(ComponentDao.class),
        mock(BlobStorageRegistry.class),
        mock(CargoAssetWriter.class),
        mock(CargoAssetReader.class),
        proxyState,
        fetcher,
        new ProxyNegativeCache(new InMemorySharedCache(), true, 1440),
        new AssetMetadataCache(new InMemorySharedCache(), false, 0, 0),
        new ObjectMapper());

    assertThrows(CargoExceptions.CargoNotFoundException.class,
        () -> service.index(runtime(), "missing-crate", false));
    assertThrows(CargoExceptions.CargoNotFoundException.class,
        () -> service.index(runtime(), "missing-crate", false));

    assertEquals(1, fetcher.calls);
  }

  @Test
  void proxyConfigAdvertisesNexusCompatibleDownloadBase() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    CargoProxyService service = new CargoProxyService(
        null, null, null, null, null, null, null, null, null, mapper);

    MavenResponse response = service.get(
        runtime(),
        new CargoPath(CargoPath.Kind.CONFIG, "config.json", null, null),
        "http://localhost/repository/cargo-proxy",
        new CargoSearchQuery("", 10, 1),
        false);

    Map<String, Object> config = mapper.readValue(
        new String(response.body().readAllBytes(), StandardCharsets.UTF_8),
        JSON_MAP);
    assertEquals("http://localhost/repository/cargo-proxy/crates", config.get("dl"));
    assertEquals("http://localhost/repository/cargo-proxy/", config.get("api"));
    assertFalse(config.containsKey("auth-required"));
  }

  @Test
  void proxyConfigHonorsAuthenticationRequirementHint() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    CargoProxyService service = new CargoProxyService(
        null, null, null, null, null, null, null, null, null, mapper);

    MavenResponse response = service.get(
        runtime(true),
        new CargoPath(CargoPath.Kind.CONFIG, "config.json", null, null),
        "http://localhost/repository/cargo-proxy",
        new CargoSearchQuery("", 10, 1),
        false);

    Map<String, Object> config = mapper.readValue(
        new String(response.body().readAllBytes(), StandardCharsets.UTF_8),
        JSON_MAP);
    assertEquals(true, config.get("auth-required"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void proxySearchUsesRemoteApiInsteadOfOnlyCachedComponents() throws Exception {
    AssetDao assetDao = mock(AssetDao.class);
    when(assetDao.findAssetByPath(anyLong(), anyString())).thenReturn(Optional.empty());
    ProxyStateDao proxyState = mock(ProxyStateDao.class);
    when(proxyState.isBlocked(anyLong(), org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(false);
    CountingFetcher fetcher = new CountingFetcher(
        jsonResult("""
            {"dl":"https://static.crates.io/crates","api":"https://crates.io"}
            """),
        jsonResult("""
            {"crates":[{"name":"itoa","max_version":"1.0.15","description":"fast integers"}],"meta":{"total":1}}
            """));
    CargoProxyService service = new CargoProxyService(
        assetDao,
        mock(ComponentDao.class),
        mock(BlobStorageRegistry.class),
        mock(CargoAssetWriter.class),
        mock(CargoAssetReader.class),
        proxyState,
        fetcher,
        new ProxyNegativeCache(new InMemorySharedCache(), true, 1440),
        new AssetMetadataCache(new InMemorySharedCache(), false, 0, 0),
        new ObjectMapper());

    MavenResponse response = service.get(
        runtime(),
        new CargoPath(CargoPath.Kind.SEARCH, "api/v1/crates", null, null),
        "http://localhost/repository/cargo-proxy",
        new CargoSearchQuery("itoa", 10, 1),
        false);

    Map<String, Object> body = new ObjectMapper().readValue(
        new String(response.body().readAllBytes(), StandardCharsets.UTF_8),
        JSON_MAP);
    List<Map<String, Object>> crates = (List<Map<String, Object>>) body.get("crates");
    assertEquals("itoa", crates.getFirst().get("name"));
    assertEquals(2, fetcher.calls);
    assertTrue(fetcher.urls.get(1).contains("https://crates.io/api/v1/crates"));
    assertTrue(fetcher.urls.get(1).contains("q=itoa"));
    assertTrue(fetcher.urls.get(1).contains("per_page=10"));
  }

  private static RepositoryRuntime runtime() {
    return runtime(null);
  }

  private static RepositoryRuntime runtime(Boolean requireAuthentication) {
    return new RepositoryRuntime(
        50,
        "cargo-proxy",
        RepositoryFormat.CARGO,
        RepositoryType.PROXY,
        "cargo-proxy",
        true,
        1L,
        null,
        null,
        null,
        true,
        REMOTE,
        0,
        0,
        true,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        requireAuthentication,
        List.of());
  }

  private static final class CountingFetcher extends HttpRemoteFetcher {
    private final Queue<Result> results;
    private final List<String> urls = new ArrayList<>();
    private int calls;

    private CountingFetcher(Result... results) {
      super(null);
      this.results = new ArrayDeque<>(List.of(results));
    }

    @Override
    public Result fetch(Request req) throws IOException {
      calls++;
      urls.add(req.url());
      return results.remove();
    }
  }

  private static HttpRemoteFetcher.Result jsonResult(String json) {
    return new HttpRemoteFetcher.Result(
        200,
        Map.of("Content-Type", "application/json"),
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }
}
