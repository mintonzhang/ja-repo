package com.github.klboke.kkrepo.migration.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klboke.kkrepo.migration.nexus.NexusRestClient.NexusInventory;
import com.github.klboke.kkrepo.migration.nexus.NexusRestClient.HttpTextResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NexusRestClientTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void reportsWarningWhenSourceScriptApiDoesNotExposePasswordHashes() throws Exception {
    FakeNexus nexus = new FakeNexus(false);
    NexusInventory inventory = client(nexus).readInventory();

    assertEquals(1, inventory.securityExport().users().size());
    assertFalse(inventory.securityExport().users().get(0).containsKey("passwordHash"));
    assertEquals(1, inventory.warnings().size());
    assertTrue(inventory.warnings().get(0).contains("HTTP 410"));
  }

  @Test
  void mergesLocalUserPasswordHashesReturnedBySourceScriptApi() throws Exception {
    FakeNexus nexus = new FakeNexus(true);
    NexusInventory inventory = client(nexus).readInventory();

    assertEquals("$shiro1$SHA-512$hash", inventory.securityExport().users().get(0).get("passwordHash"));
    assertEquals(1, inventory.securityExport().apiKeys().size());
    assertEquals("NpmToken", inventory.securityExport().apiKeys().get(0).get("domain"));
    assertEquals("alice-token", inventory.securityExport().apiKeys().get(0).get("api_key"));
    assertEquals(1, inventory.securityExport().roles().size());
    assertEquals("nx-admin", inventory.securityExport().roles().get(0).get("id"));
    assertEquals(List.of(), inventory.securityExport().realmOrder());
    assertEquals(List.of(), inventory.warnings());
    assertTrue(nexus.scriptDeleted);
    assertEquals("/service/rest/v1/security/users?source=default", nexus.usersPath);
    assertEquals("/service/rest/v1/security/roles?source=default", nexus.rolesPath);
  }

  @Test
  void includesApiPathWhenSourceRequestTimesOut() {
    NexusRestClient client = new NexusRestClient(
        "http://source.example/",
        "admin",
        "secret",
        OBJECT_MAPPER,
        false,
        request -> {
          throw new HttpTimeoutException("request timed out");
        });

    IOException thrown = assertThrows(IOException.class, client::readInventory);

    assertEquals("Nexus API /service/rest/v1/repositories timed out after 30s", thrown.getMessage());
    assertTrue(thrown.getCause() instanceof HttpTimeoutException);
  }

  @Test
  void resolvesDockerDefaultGatewayForLocalSourceUrl() {
    String routeTable = """
        Iface Destination Gateway Flags RefCnt Use Metric Mask MTU Window IRTT
        eth0 00000000 01D7A8C0 0003 0 0 0 00000000 0 0 0
        """;

    assertEquals("192.168.215.1", NexusRestClient.dockerDefaultGatewayHost(routeTable));
    assertEquals(
        URI.create("http://192.168.215.1:28090/"),
        NexusRestClient.dockerGatewayBaseUri(URI.create("http://localhost:28090/"), routeTable));
  }

  @Test
  void sourceSecurityScriptUsesClassLookupForNamedComponents() throws Exception {
    var field = NexusRestClient.class.getDeclaredField("LOCAL_SECURITY_EXPORT_SCRIPT");
    field.setAccessible(true);
    String script = (String) field.get(null);

    assertTrue(script.contains("container.lookup(DatabaseInstance.class, DatabaseInstanceNames.SECURITY)"));
    assertTrue(script.contains("container.lookup(DatabaseInstance.class, 'security')"));
    assertFalse(script.contains("DatabaseInstance.class.name,"));
    assertFalse(script.contains("SecurityConfigurationManager.class.name, 'default'"));
    assertFalse(script.contains("ClassLoader.class.name, 'nexus-uber'"));
  }

  @Test
  void repositoryDataScriptPagesAssetsByBucketAndNameCursor() throws Exception {
    var field = NexusRestClient.class.getDeclaredField("LOCAL_REPOSITORY_DATA_EXPORT_SCRIPT");
    field.setAccessible(true);
    String script = (String) field.get(null);

    assertTrue(script.contains("DatabaseInstanceNames.COMPONENT"));
    assertTrue(script.contains("where bucket = ? and name > ? order by name limit"));
    assertTrue(script.contains("def changedSince = { asset ->"));
    assertTrue(script.contains("asset.field('blob_updated')"));
    assertTrue(script.contains("asset.field('blob_created')"));
    assertTrue(script.contains("blobUpdated == null && blobCreated == null"));
    assertTrue(script.contains("def nextAfterPath = rows.isEmpty()"));
    assertTrue(script.contains("complete: rows.size() < pageSize"));
    assertFalse(script.contains("blob_updated >= ?"));
    assertFalse(script.contains("blob_created >= ?"));
    assertTrue(script.contains("Date.from(Instant.parse(since))"));
    assertTrue(script.contains("new JsonSlurper().parseText(args)"));
  }

  @Test
  void readsRepositoryDataPageFromSourceScriptApi() throws Exception {
    RepositoryDataFakeNexus nexus = new RepositoryDataFakeNexus();
    NexusRestClient client = new NexusRestClient(
        "http://source.example/",
        "admin",
        "secret",
        OBJECT_MAPPER,
        false,
        nexus::send);

    NexusRestClient.RepositoryAssetPage page;
    try (NexusRestClient.RepositoryDataScriptSession session = client.openRepositoryDataScript()) {
      page = session.readPage("maven-releases", "com/acme/demo", 1000);
    }

    assertEquals("maven-releases", page.repositoryName());
    assertEquals("com/acme/demo", page.afterPath());
    assertEquals("com/acme/demo/1.0/demo-1.0.jar", page.nextAfterPath());
    assertTrue(page.complete());
    assertEquals(1, page.assets().size());
    assertEquals("com.acme", page.assets().get(0).namespace());
    assertEquals("demo", page.assets().get(0).name());
    assertEquals("1.0", page.assets().get(0).version());
    assertTrue(nexus.scriptDeleted);
  }

  @Test
  void dockerManifestAssetDownloadsRequestSchema2ManifestMediaTypes() {
    String accept = NexusRestClient.repositoryAssetAccept("v2/team/app/manifests/latest");

    assertTrue(accept.contains("application/vnd.docker.distribution.manifest.v2+json"));
    assertTrue(accept.contains("application/vnd.oci.image.manifest.v1+json"));
    assertEquals("*/*", NexusRestClient.repositoryAssetAccept("v2/team/app/blobs/sha256:abc"));
  }

  private static NexusRestClient client(FakeNexus nexus) {
    return new NexusRestClient(
        "http://source.example/",
        "admin",
        "secret",
        OBJECT_MAPPER,
        false,
        nexus::send);
  }

  private static final class FakeNexus {
    private final boolean scriptApiEnabled;
    private boolean scriptDeleted;
    private String usersPath;
    private String rolesPath;

    private FakeNexus(boolean scriptApiEnabled) {
      this.scriptApiEnabled = scriptApiEnabled;
    }

    private HttpTextResponse send(HttpRequest request) throws IOException {
      String method = request.method();
      String path = request.uri().getPath();
      if (request.uri().getRawQuery() != null) {
        path += "?" + request.uri().getRawQuery();
      }
      if ("GET".equals(method) && "/service/rest/v1/repositories".equals(path)) {
        return json(200, List.of());
      }
      if ("GET".equals(method) && "/service/rest/v1/blobstores".equals(path)) {
        return json(200, List.of());
      }
      if ("GET".equals(method) && "/service/rest/v1/security/users?source=default".equals(path)) {
        usersPath = path;
        return json(200, List.of(
            Map.of(
                "userId", "alice",
                "source", "default",
                "roles", List.of("nx-admin")),
            Map.of(
                "userId", "ldap-user",
                "source", "LDAP",
                "roles", List.of("nx-anonymous"))));
      }
      if ("GET".equals(method) && "/service/rest/v1/security/roles?source=default".equals(path)) {
        rolesPath = path;
        return json(200, List.of(
            Map.of(
                "id", "nx-admin",
                "name", "nx-admin",
                "source", "default",
                "privileges", List.of("nx-all"),
                "roles", List.of()),
            Map.of(
                "id", "ldap-role",
                "name", "ldap-role",
                "source", "LDAP",
                "privileges", List.of(),
                "roles", List.of())));
      }
      if ("GET".equals(method) && "/service/rest/v1/security/privileges".equals(path)) {
        return json(200, List.of());
      }
      if ("GET".equals(method) && "/service/rest/v1/security/content-selectors".equals(path)) {
        return json(200, List.of());
      }
      if ("GET".equals(method) && "/service/rest/v1/security/realms/active".equals(path)) {
        return json(200, List.of("NexusAuthenticatingRealm"));
      }
      if ("GET".equals(method) && "/service/rest/v1/security/anonymous".equals(path)) {
        return json(200, Map.of("enabled", false));
      }
      if ("POST".equals(method) && "/service/rest/v1/script".equals(path)) {
        if (!scriptApiEnabled) {
          return json(410, Map.of("message", "scripts disabled"));
        }
        return empty(204);
      }
      if ("POST".equals(method)
          && path.startsWith("/service/rest/v1/script/")
          && path.endsWith("/run")) {
        String result = OBJECT_MAPPER.writeValueAsString(Map.of(
            "users", List.of(Map.of(
                "userId", "alice",
                "source", "default",
                "passwordHash", "$shiro1$SHA-512$hash")),
            "apiKeys", List.of(Map.of(
                "domain", "NpmToken",
                "api_key", "alice-token",
                "primary_principal", "alice",
                "principals", Map.of(
                    "primaryPrincipal", "alice",
                    "realmNames", List.of("NexusAuthenticatingRealm"))))));
        return json(200, Map.of("name", "password-export", "result", result));
      }
      if ("DELETE".equals(method) && path.startsWith("/service/rest/v1/script/")) {
        scriptDeleted = true;
        return empty(204);
      }
      return json(404, Map.of("message", "not found", "path", path));
    }

    private static HttpTextResponse json(int status, Object body) throws IOException {
      return new HttpTextResponse(status, OBJECT_MAPPER.writeValueAsString(body));
    }

    private static HttpTextResponse empty(int status) {
      return new HttpTextResponse(status, "");
    }
  }

  private static final class RepositoryDataFakeNexus {
    private boolean scriptDeleted;

    private HttpTextResponse send(HttpRequest request) throws IOException {
      String method = request.method();
      String path = request.uri().getPath();
      if ("POST".equals(method) && "/service/rest/v1/script".equals(path)) {
        return new HttpTextResponse(204, "");
      }
      if ("POST".equals(method)
          && path.startsWith("/service/rest/v1/script/")
          && path.endsWith("/run")) {
        LinkedHashMap<String, Object> asset = new LinkedHashMap<>();
        asset.put("repositoryName", "maven-releases");
        asset.put("assetId", "#45:1");
        asset.put("componentId", "#44:1");
        asset.put("path", "com/acme/demo/1.0/demo-1.0.jar");
        asset.put("format", "maven2");
        asset.put("namespace", "com.acme");
        asset.put("name", "demo");
        asset.put("version", "1.0");
        asset.put("contentType", "application/java-archive");
        asset.put("size", 123L);
        asset.put("sourceBlobRef", "default@abc");
        String result = OBJECT_MAPPER.writeValueAsString(Map.of(
            "repositoryName", "maven-releases",
            "afterPath", "com/acme/demo",
            "nextAfterPath", "com/acme/demo/1.0/demo-1.0.jar",
            "complete", true,
            "warnings", List.of(),
            "assets", List.of(asset)));
        return new HttpTextResponse(200, OBJECT_MAPPER.writeValueAsString(Map.of(
            "name", "repository-data-export",
            "result", result)));
      }
      if ("DELETE".equals(method) && path.startsWith("/service/rest/v1/script/")) {
        scriptDeleted = true;
        return new HttpTextResponse(204, "");
      }
      return new HttpTextResponse(404, OBJECT_MAPPER.writeValueAsString(Map.of("message", "not found")));
    }
  }

}
