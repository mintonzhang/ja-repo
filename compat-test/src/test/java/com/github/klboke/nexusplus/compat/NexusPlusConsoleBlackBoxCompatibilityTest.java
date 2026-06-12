package com.github.klboke.nexusplus.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class NexusPlusConsoleBlackBoxCompatibilityTest {
  private static final Pattern NON_EMPTY_ACCESS_KEY =
      Pattern.compile("\"accessKey\"\\s*:\\s*\"[^\"]+\"");

  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  @Test
  void componentSearchEndpointIsBackedByRealApiWhenConfigured() throws Exception {
    String baseUrl = nexusPlusBaseUrl();
    assumeTrue(baseUrl != null, "Set NEXUS_PLUS_COMPAT_BASE_URL to run console black-box checks");

    HttpResponse<String> response = get(baseUrl, "/internal/search/components?format=maven2&limit=5");

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("\"items\""), "search response should expose item list");
    assertFalse(response.body().contains("Search index not implemented"),
        "search UI/API must not be placeholder-backed");
  }

  @Test
  void blobStoreListDoesNotExposeStoredCredentialsWhenConfigured() throws Exception {
    String baseUrl = nexusPlusBaseUrl();
    assumeTrue(baseUrl != null, "Set NEXUS_PLUS_COMPAT_BASE_URL to run console black-box checks");

    HttpResponse<String> response = get(baseUrl, "/internal/blob-stores");

    assertEquals(200, response.statusCode());
    assertFalse(NON_EMPTY_ACCESS_KEY.matcher(response.body()).find(),
        "blob-store list must not leak accessKey values");
    assertFalse(response.body().contains("secretKey"),
        "blob-store list must not leak secretKey fields");
    assertTrue(response.body().contains("\"accessKeyConfigured\""),
        "credential presence should be exposed as metadata only");
  }

  private static HttpResponse<String> get(String baseUrl, String path) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
        .timeout(Duration.ofSeconds(10))
        .header("Accept", "application/json");
    CompatDefaults.nexusPlusUsername().ifPresent(username ->
        CompatDefaults.nexusPlusPassword().ifPresent(password ->
            builder.header("Authorization", basic(username, password))));
    return HTTP.send(
        builder.GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static String nexusPlusBaseUrl() {
    return CompatDefaults.nexusPlusBaseUrl().orElseThrow();
  }

  private static String basic(String username, String password) {
    String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    return "Basic " + token;
  }
}
