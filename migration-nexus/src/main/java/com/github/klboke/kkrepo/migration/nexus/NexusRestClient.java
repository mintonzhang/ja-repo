package com.github.klboke.kkrepo.migration.nexus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klboke.kkrepo.migration.nexus.security.NexusSecurityExport;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class NexusRestClient {
  private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
      new TypeReference<>() {
      };
  private static final TypeReference<Map<String, Object>> MAP =
      new TypeReference<>() {
      };
  private static final TypeReference<List<String>> LIST_OF_STRINGS =
      new TypeReference<>() {
      };
  private static final String LOCAL_USER_SOURCE = "default";
  private static final String LOCAL_USERS_PATH = "/service/rest/v1/security/users?source=default";
  private static final String LOCAL_ROLES_PATH = "/service/rest/v1/security/roles?source=default";
  private static final String DOCKER_MANIFEST_ACCEPT = String.join(", ",
      "application/vnd.docker.distribution.manifest.v2+json",
      "application/vnd.docker.distribution.manifest.list.v2+json",
      "application/vnd.oci.image.manifest.v1+json",
      "application/vnd.oci.image.index.v1+json",
      "application/vnd.oci.artifact.manifest.v1+json");
  private static final String LOCAL_SECURITY_EXPORT_SCRIPT = """
      import groovy.json.JsonOutput
      import java.io.ByteArrayInputStream
      import org.sonatype.nexus.common.io.ObjectInputStreamWithClassLoader
      import org.sonatype.nexus.orient.DatabaseInstance
      import org.sonatype.nexus.orient.DatabaseInstanceNames
      import org.sonatype.nexus.security.config.SecurityConfigurationManager
      import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

      def security
      try {
        security = container.lookup(SecurityConfigurationManager.class.name)
      } catch (Throwable ignored) {
        security = container.lookup(SecurityConfigurationManager.class, 'default')
      }
      def users = security.listUsers().collect { user ->
        [
          userId: user.id,
          source: 'default',
          passwordHash: user.password
        ]
      }
      def warnings = []
      def principalDetails = { bytes ->
        if (bytes == null) {
          return [:]
        }
        try {
          def uberClassLoader = container.lookup(ClassLoader.class, 'nexus-uber')
          def input = new ObjectInputStreamWithClassLoader(new ByteArrayInputStream(bytes), uberClassLoader)
          try {
            def principals = input.readObject()
            return [
              primaryPrincipal: principals.primaryPrincipal == null ? null : String.valueOf(principals.primaryPrincipal),
              realmNames: principals.realmNames == null ? [] : principals.realmNames.collect { String.valueOf(it) },
              principals: principals.asList() == null ? [] : principals.asList().collect { String.valueOf(it) }
            ]
          } finally {
            input.close()
          }
        } catch (Throwable ignored) {
          return [:]
        }
      }
      def apiKeys = []
      try {
        def securityDatabase
        try {
          securityDatabase = container.lookup(DatabaseInstance.class, DatabaseInstanceNames.SECURITY)
        } catch (Throwable ignored) {
          securityDatabase = container.lookup(DatabaseInstance.class, 'security')
        }
        def db = securityDatabase.connect()
        try {
          db.query(new OSQLSynchQuery('select from api_key')).each { document ->
            def principals = principalDetails(document.field('principals'))
            apiKeys << [
              domain: document.field('domain'),
              api_key: document.field('api_key'),
              primary_principal: document.field('primary_principal'),
              principals: principals
            ]
          }
        } finally {
          db.close()
        }
      } catch (Throwable e) {
        warnings << "Source Nexus script API did not expose API keys: " + e.class.name + ": " + String.valueOf(e.message)
        apiKeys = []
      }
      return JsonOutput.toJson([users: users, apiKeys: apiKeys, warnings: warnings])
      """;
  private static final String LOCAL_REPOSITORY_DATA_EXPORT_SCRIPT = """
      import groovy.json.JsonOutput
      import groovy.json.JsonSlurper
      import java.time.Instant
      import org.sonatype.nexus.orient.DatabaseInstance
      import org.sonatype.nexus.orient.DatabaseInstanceNames
      import com.orientechnologies.orient.core.id.ORID
      import com.orientechnologies.orient.core.record.impl.ODocument
      import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

      def request = args == null || args.trim().isEmpty()
          ? [:]
          : new JsonSlurper().parseText(args)
      def repositoryName = String.valueOf(request.repositoryName ?: '').trim()
      if (repositoryName.isEmpty()) {
        throw new IllegalArgumentException('repositoryName is required')
      }
      int pageSize = Math.max(1, Math.min(((request.pageSize ?: 1000) as Integer), 5000))
      def afterPath = request.afterPath == null ? '' : String.valueOf(request.afterPath)
      def since = request.since == null ? '' : String.valueOf(request.since).trim()
      def sinceDate = since.isEmpty() ? null : Date.from(Instant.parse(since))

      def componentDatabase
      try {
        componentDatabase = container.lookup(DatabaseInstance.class, DatabaseInstanceNames.COMPONENT)
      } catch (Throwable ignored) {
        componentDatabase = container.lookup(DatabaseInstance.class, 'component')
      }

      def normalize
      normalize = { value ->
        if (value == null) {
          return null
        }
        if (value instanceof Date) {
          return value.toInstant().toString()
        }
        if (value instanceof ORID) {
          return String.valueOf(value)
        }
        if (value instanceof ODocument) {
          return normalize(value.toMap())
        }
        if (value instanceof Map) {
          def out = new LinkedHashMap()
          value.each { k, v -> out[String.valueOf(k)] = normalize(v) }
          return out
        }
        if (value instanceof Iterable) {
          return value.collect { normalize(it) }
        }
        return value
      }
      def iso = { value -> value instanceof Date ? value.toInstant().toString() : null }
      def text = { value ->
        if (value == null) {
          return null
        }
        def s = String.valueOf(value)
        return s.isEmpty() ? null : s
      }
      def changedSince = { asset ->
        if (sinceDate == null) {
          return true
        }
        def blobUpdated = asset.field('blob_updated')
        if (blobUpdated instanceof Date && !blobUpdated.before(sinceDate)) {
          return true
        }
        def blobCreated = asset.field('blob_created')
        if (blobCreated instanceof Date && !blobCreated.before(sinceDate)) {
          return true
        }
        if (blobUpdated == null && blobCreated == null) {
          def lastUpdated = asset.field('last_updated')
          return lastUpdated instanceof Date && !lastUpdated.before(sinceDate)
        }
        return false
      }

      def db = componentDatabase.connect()
      try {
        def buckets = db.query(new OSQLSynchQuery('select from bucket where repository_name = ?'), repositoryName)
        if (buckets == null || buckets.isEmpty()) {
          return JsonOutput.toJson([
            repositoryName: repositoryName,
            afterPath: afterPath,
            nextAfterPath: afterPath,
            complete: true,
            assets: [],
            warnings: ['source repository bucket not found: ' + repositoryName]
          ])
        }
        def bucket = buckets[0]
        def query
        def rows
        query = afterPath.isEmpty()
            ? 'select from asset where bucket = ? order by name limit ' + pageSize
            : 'select from asset where bucket = ? and name > ? order by name limit ' + pageSize
        rows = afterPath.isEmpty()
            ? db.query(new OSQLSynchQuery(query), bucket.getIdentity())
            : db.query(new OSQLSynchQuery(query), bucket.getIdentity(), afterPath)
        rows = rows ?: []
        def assets = []
        rows.each { asset ->
          if (!changedSince(asset)) {
            return
          }
          def component = asset.field('component')
          if (component instanceof ORID) {
            component = db.load(component)
          }
          def componentAttributes = component == null ? [:] : normalize(component.field('attributes') ?: [:])
          def assetAttributes = normalize(asset.field('attributes') ?: [:])
          assets << [
            repositoryName: repositoryName,
            assetId: String.valueOf(asset.getIdentity()),
            componentId: component == null ? null : String.valueOf(component.getIdentity()),
            path: text(asset.field('name')),
            format: text(asset.field('format')),
            namespace: component == null ? null : text(component.field('group')),
            name: component == null ? null : text(component.field('name')),
            version: component == null ? null : text(component.field('version')),
            assetKind: text(asset.field('asset_kind')),
            contentType: text(asset.field('content_type')),
            size: asset.field('size'),
            sourceBlobRef: text(asset.field('blob_ref')),
            lastUpdated: iso(asset.field('last_updated')),
            lastDownloaded: iso(asset.field('last_downloaded')),
            blobCreated: iso(asset.field('blob_created')),
            blobUpdated: iso(asset.field('blob_updated')),
            createdBy: text(asset.field('created_by')),
            createdByIp: text(asset.field('created_by_ip')),
            attributes: assetAttributes,
            componentAttributes: componentAttributes
          ]
        }
        def nextAfterPath = rows.isEmpty() ? afterPath : text(rows[-1].field('name'))
        return JsonOutput.toJson([
          repositoryName: repositoryName,
          afterPath: afterPath,
          since: since,
          nextAfterPath: nextAfterPath,
          complete: rows.size() < pageSize,
          assets: assets,
          warnings: []
        ])
      } finally {
        db.close()
      }
      """;

  private final List<URI> baseUris;
  private final String authorization;
  private final ObjectMapper objectMapper;
  private final HttpTransport transport;
  private final HttpClient binaryClient;

  public NexusRestClient(
      String baseUrl,
      String username,
      String password,
      ObjectMapper objectMapper) {
    this(baseUrl, username, password, objectMapper, true);
  }

  NexusRestClient(
      String baseUrl,
      String username,
      String password,
      ObjectMapper objectMapper,
      boolean dockerLocalhostFallback) {
    this(baseUrl, username, password, objectMapper, dockerLocalhostFallback, defaultTransport());
  }

  NexusRestClient(
      String baseUrl,
      String username,
      String password,
      ObjectMapper objectMapper,
      boolean dockerLocalhostFallback,
      HttpTransport transport) {
    URI baseUri = normalizeBaseUri(baseUrl);
    this.baseUris = dockerLocalhostFallback ? candidateBaseUris(baseUri) : List.of(baseUri);
    this.authorization = basic(username, password);
    this.objectMapper = objectMapper;
    this.transport = transport == null ? defaultTransport() : transport;
    this.binaryClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  private static HttpTransport defaultTransport() {
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    return request -> {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return new HttpTextResponse(response.statusCode(), response.body());
    };
  }

  public NexusInventory readInventory() throws IOException, InterruptedException {
    List<Map<String, Object>> repositories = getList("/service/rest/v1/repositories");
    List<RepositoryDocument> repositoryDocuments = new ArrayList<>(repositories.size());
    for (Map<String, Object> repository : repositories) {
      String name = string(repository.get("name"));
      String format = string(repository.get("format"));
      String type = string(repository.get("type"));
      Map<String, Object> detail = Map.of();
      if (NexusRepositorySupport.supportedRecipe(format, type)) {
        detail = getMap("/service/rest/v1/repositories/"
            + endpointFormat(format)
            + "/"
            + lower(type)
            + "/"
            + encodePathSegment(name));
      }
      repositoryDocuments.add(new RepositoryDocument(repository, detail));
    }
    SecurityExportResult security = readSecurityExport();
    return new NexusInventory(
        getList("/service/rest/v1/blobstores"),
        repositoryDocuments,
        security.export(),
        security.warnings());
  }

  public RepositoryDataScriptSession openRepositoryDataScript()
      throws IOException, InterruptedException {
    String scriptName = "kkrepo-repository-data-export-" + UUID.randomUUID().toString().replace("-", "");
    Map<String, Object> createRequest = Map.of(
        "name", scriptName,
        "type", "groovy",
        "content", LOCAL_REPOSITORY_DATA_EXPORT_SCRIPT);
    HttpTextResponse created = postJson("/service/rest/v1/script", createRequest);
    if (!created.success()) {
      throw new IOException("Source Nexus script API did not create repository data export script: "
          + created.describe());
    }
    return new RepositoryDataScriptSession(scriptName);
  }

  public HttpResponse<InputStream> getRepositoryAsset(String repositoryName, String path)
      throws IOException, InterruptedException {
    String requestPath = "/repository/"
        + encodePathSegment(repositoryName)
        + "/"
        + encodePath(path);
    IOException firstFailure = null;
    String accept = repositoryAssetAccept(path);
    for (int index = 0; index < baseUris.size(); index++) {
      HttpRequest request = requestBuilder(baseUris.get(index), requestPath)
          .header("Accept", accept)
          .GET()
          .build();
      try {
        return binaryClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      } catch (HttpTimeoutException e) {
        IOException failure = new IOException(timeoutMessage(requestPath, request), e);
        if (index + 1 >= baseUris.size()) {
          if (firstFailure != null) {
            failure.addSuppressed(firstFailure);
          }
          throw failure;
        }
        if (firstFailure == null) {
          firstFailure = failure;
        }
      } catch (IOException e) {
        if (index + 1 >= baseUris.size()) {
          if (firstFailure != null) {
            e.addSuppressed(firstFailure);
          }
          throw e;
        }
        if (firstFailure == null) {
          firstFailure = e;
        }
      }
    }
    throw new IOException("Nexus repository asset " + requestPath + " did not return a response");
  }

  static String repositoryAssetAccept(String path) {
    if (path == null) {
      return "*/*";
    }
    String normalized = path.trim();
    if (normalized.contains("/manifests/") && (normalized.startsWith("v2/") || normalized.contains("/v2/"))) {
      return DOCKER_MANIFEST_ACCEPT;
    }
    return "*/*";
  }

  private SecurityExportResult readSecurityExport() throws IOException, InterruptedException {
    List<Map<String, Object>> users = getList(LOCAL_USERS_PATH).stream()
        .filter(NexusRestClient::isLocalUser)
        .toList();
    SecurityScriptProbe securityScriptProbe = readLocalSecurityScriptExport();
    List<Map<String, Object>> mergedUsers = mergePasswordHashes(users, securityScriptProbe.passwordHashes());
    List<Map<String, Object>> userRoleMappings = users.stream()
        .map(user -> {
          LinkedHashMap<String, Object> mapping = new LinkedHashMap<>();
          mapping.put("userId", firstString(user, "userId", "id"));
          mapping.put("source", firstString(user, "source"));
          mapping.put("roles", stringList(user.get("roles")));
          return Map.copyOf(mapping);
        })
        .toList();
    return new SecurityExportResult(new NexusSecurityExport(
        mergedUsers,
        getList(LOCAL_ROLES_PATH).stream()
            .filter(NexusRestClient::isLocalRole)
            .toList(),
        getList("/service/rest/v1/security/privileges"),
        userRoleMappings,
        securityScriptProbe.apiKeys(),
        getList("/service/rest/v1/security/content-selectors"),
        List.of(),
        List.of(),
        getMap("/service/rest/v1/security/anonymous")),
        securityScriptProbe.warnings());
  }

  private static boolean isLocalUser(Map<String, Object> user) {
    String source = firstString(user, "source");
    return source == null || LOCAL_USER_SOURCE.equalsIgnoreCase(source);
  }

  private static boolean isLocalRole(Map<String, Object> role) {
    String source = firstString(role, "source");
    return source == null || LOCAL_USER_SOURCE.equalsIgnoreCase(source);
  }

  private SecurityScriptProbe readLocalSecurityScriptExport() throws IOException, InterruptedException {
    String scriptName = "kkrepo-security-export-" + UUID.randomUUID().toString().replace("-", "");
    Map<String, Object> createRequest = Map.of(
        "name", scriptName,
        "type", "groovy",
        "content", LOCAL_SECURITY_EXPORT_SCRIPT);
    HttpTextResponse created = postJson("/service/rest/v1/script", createRequest);
    if (!created.success()) {
      return SecurityScriptProbe.warning("Source Nexus script API did not expose local user password hashes or API keys: "
          + created.describe());
    }
    try {
      HttpTextResponse run = postText("/service/rest/v1/script/"
          + encodePathSegment(scriptName)
          + "/run", "");
      if (!run.success()) {
        return SecurityScriptProbe.warning("Source Nexus script API did not return local user password hashes or API keys: "
            + run.describe());
      }
      Map<String, Object> document = objectMapper.readValue(run.body(), MAP);
      String result = string(document.get("result"));
      if (result == null) {
        return SecurityScriptProbe.warning("Source Nexus script API returned an empty local user password hash/API key result.");
      }
      return securityScriptProbeFromScriptResult(result);
    } finally {
      delete("/service/rest/v1/script/" + encodePathSegment(scriptName));
    }
  }

  private SecurityScriptProbe securityScriptProbeFromScriptResult(String result) throws IOException {
    Map<String, Object> document = objectMapper.readValue(result, MAP);
    Object users = document.get("users");
    LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
    if (users instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        if (!(item instanceof Map<?, ?> rawUser)) {
          continue;
        }
        Map<String, Object> user = objectMap(rawUser);
        String userId = firstString(user, "userId", "id");
        String hash = firstString(user, "passwordHash", "password", "password_hash");
        if (userId != null && hash != null) {
          hashes.put(userKey(firstString(user, "source"), userId), hash);
        }
      }
    }
    return new SecurityScriptProbe(
        Map.copyOf(hashes),
        objectMaps(document.get("apiKeys")),
        stringList(document.get("warnings")));
  }

  private static List<Map<String, Object>> mergePasswordHashes(
      List<Map<String, Object>> users,
      Map<String, String> passwordHashes) {
    if (passwordHashes.isEmpty()) {
      return users;
    }
    ArrayList<Map<String, Object>> merged = new ArrayList<>(users.size());
    for (Map<String, Object> user : users) {
      String userId = firstString(user, "userId", "id");
      String key = userKey(firstString(user, "source"), userId);
      String hash = passwordHashes.get(key);
      if (hash == null || firstString(user, "passwordHash", "password", "password_hash") != null) {
        merged.add(user);
        continue;
      }
      LinkedHashMap<String, Object> copy = new LinkedHashMap<>(user);
      copy.put("passwordHash", hash);
      merged.add(Map.copyOf(copy));
    }
    return List.copyOf(merged);
  }

  private List<Map<String, Object>> getList(String path) throws IOException, InterruptedException {
    return objectMapper.readValue(get(path), LIST_OF_MAPS);
  }

  private Map<String, Object> getMap(String path) throws IOException, InterruptedException {
    return objectMapper.readValue(get(path), MAP);
  }

  private List<String> getStringList(String path) throws IOException, InterruptedException {
    return objectMapper.readValue(get(path), LIST_OF_STRINGS);
  }

  private String get(String path) throws IOException, InterruptedException {
    HttpTextResponse response = send(path, baseUri -> requestBuilder(baseUri, path)
        .GET()
        .build());
    if (!response.success()) {
      throw new IOException("Nexus API " + path + " returned HTTP " + response.statusCode()
          + ": " + truncate(response.body()));
    }
    return response.body();
  }

  private HttpTextResponse postJson(String path, Object body) throws IOException, InterruptedException {
    String payload = objectMapper.writeValueAsString(body);
    return send(path, baseUri -> requestBuilder(baseUri, path)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build());
  }

  private HttpTextResponse postText(String path, String body) throws IOException, InterruptedException {
    return send(path, baseUri -> requestBuilder(baseUri, path)
        .header("Content-Type", "text/plain")
        .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
        .build());
  }

  private void delete(String path) throws IOException, InterruptedException {
    send(path, baseUri -> requestBuilder(baseUri, path)
        .DELETE()
        .build());
  }

  private HttpTextResponse send(String path, RequestFactory factory) throws IOException, InterruptedException {
    IOException firstFailure = null;
    for (int index = 0; index < baseUris.size(); index++) {
      HttpRequest request = null;
      try {
        request = factory.build(baseUris.get(index));
        return transport.send(request);
      } catch (HttpTimeoutException e) {
        IOException failure = new IOException(timeoutMessage(path, request), e);
        if (index + 1 >= baseUris.size()) {
          if (firstFailure != null) {
            failure.addSuppressed(firstFailure);
          }
          throw failure;
        }
        if (firstFailure == null) {
          firstFailure = failure;
        }
      } catch (IOException e) {
        if (index + 1 >= baseUris.size()) {
          if (firstFailure != null) {
            e.addSuppressed(firstFailure);
          }
          throw e;
        }
        if (firstFailure == null) {
          firstFailure = e;
        }
      }
    }
    throw new IOException("Nexus API " + path + " did not return a response");
  }

  private static String timeoutMessage(String path, HttpRequest request) {
    String timeout = request == null
        ? "the configured timeout"
        : request.timeout()
            .map(duration -> duration.toSeconds() + "s")
            .orElse("the configured timeout");
    return "Nexus API " + path + " timed out after " + timeout;
  }

  private HttpRequest.Builder requestBuilder(URI baseUri, String path) {
    return HttpRequest.newBuilder(baseUri.resolve(path))
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/json")
        .header("Authorization", authorization);
  }

  private static URI normalizeBaseUri(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("sourceBaseUrl is required");
    }
    String value = baseUrl.trim();
    if (!value.endsWith("/")) {
      value += "/";
    }
    return URI.create(value);
  }

  private static List<URI> candidateBaseUris(URI primary) {
    if (!isLocalhost(primary)) {
      return List.of(primary);
    }
    LinkedHashMap<String, URI> candidates = new LinkedHashMap<>();
    addCandidate(candidates, primary);
    addCandidate(candidates, dockerGatewayBaseUri(primary));
    addCandidate(candidates, dockerHostBaseUri(primary));
    return List.copyOf(candidates.values());
  }

  private static void addCandidate(Map<String, URI> candidates, URI candidate) {
    if (candidate != null) {
      candidates.putIfAbsent(candidate.toString(), candidate);
    }
  }

  private static URI dockerHostBaseUri(URI uri) {
    if (!isLocalhost(uri)) {
      return null;
    }
    return withHost(uri, "host.docker.internal");
  }

  private static URI dockerGatewayBaseUri(URI uri) {
    try {
      return dockerGatewayBaseUri(uri, Files.readString(Path.of("/proc/net/route")));
    } catch (IOException e) {
      return null;
    }
  }

  static URI dockerGatewayBaseUri(URI uri, String procNetRoute) {
    if (!isLocalhost(uri)) {
      return null;
    }
    String gateway = dockerDefaultGatewayHost(procNetRoute);
    return gateway == null ? null : withHost(uri, gateway);
  }

  static String dockerDefaultGatewayHost(String procNetRoute) {
    if (procNetRoute == null || procNetRoute.isBlank()) {
      return null;
    }
    for (String line : procNetRoute.split("\\R")) {
      String[] columns = line.trim().split("\\s+");
      if (columns.length < 3
          || !"00000000".equals(columns[1])
          || "00000000".equals(columns[2])) {
        continue;
      }
      try {
        long gateway = Long.parseLong(columns[2], 16);
        return (gateway & 0xff)
            + "."
            + ((gateway >> 8) & 0xff)
            + "."
            + ((gateway >> 16) & 0xff)
            + "."
            + ((gateway >> 24) & 0xff);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static boolean isLocalhost(URI uri) {
    String host = uri.getHost();
    if (host == null) {
      return false;
    }
    String normalized = host.toLowerCase(Locale.ROOT);
    return "localhost".equals(normalized)
        || "127.0.0.1".equals(normalized)
        || "0.0.0.0".equals(normalized)
        || "::1".equals(normalized);
  }

  private static URI withHost(URI uri, String host) {
    try {
      return new URI(
          uri.getScheme(),
          uri.getUserInfo(),
          host,
          uri.getPort(),
          uri.getPath(),
          uri.getQuery(),
          uri.getFragment());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid sourceBaseUrl: " + uri, e);
    }
  }

  private static String basic(String username, String password) {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("source username is required");
    }
    String raw = username + ":" + (password == null ? "" : password);
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static String endpointFormat(String format) {
    if ("maven2".equalsIgnoreCase(format)) {
      return "maven";
    }
    return lower(format);
  }

  private static String encodePathSegment(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
        .replace("+", "%20");
  }

  private static String encodePath(String path) {
    if (path == null || path.isBlank()) {
      return "";
    }
    String[] segments = path.split("/", -1);
    StringBuilder encoded = new StringBuilder(path.length() + 16);
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        encoded.append('/');
      }
      encoded.append(encodePathSegment(segments[i]));
    }
    return encoded.toString();
  }

  private static String firstString(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      String value = string(map.get(key));
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static List<String> stringList(Object value) {
    if (value instanceof Iterable<?> iterable) {
      ArrayList<String> values = new ArrayList<>();
      for (Object item : iterable) {
        String text = string(item);
        if (text != null) {
          values.add(text);
        }
      }
      return List.copyOf(values);
    }
    String text = string(value);
    return text == null ? List.of() : List.of(text);
  }

  private static Map<String, Object> objectMap(Map<?, ?> source) {
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> {
      String normalizedKey = string(key);
      if (normalizedKey != null && value != null) {
        copy.put(normalizedKey, value);
      }
    });
    return Map.copyOf(copy);
  }

  private static List<Map<String, Object>> objectMaps(Object value) {
    if (!(value instanceof Iterable<?> iterable)) {
      return List.of();
    }
    ArrayList<Map<String, Object>> maps = new ArrayList<>();
    for (Object item : iterable) {
      if (item instanceof Map<?, ?> map) {
        maps.add(objectMap(map));
      }
    }
    return List.copyOf(maps);
  }

  private static String string(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static String lower(String value) {
    return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
  }

  private static String userKey(String source, String userId) {
    return normalizeSource(source) + "/" + string(userId);
  }

  private static String normalizeSource(String source) {
    String normalized = string(source);
    if (normalized == null) {
      return "local";
    }
    String lower = normalized.toLowerCase(Locale.ROOT);
    if ("default".equals(lower)
        || "nexus".equals(lower)
        || "local".equals(lower)
        || "nexusauthenticatingrealm".equals(lower)
        || "nexusauthorizingrealm".equals(lower)) {
      return "local";
    }
    return lower;
  }

  private static String truncate(String body) {
    if (body == null) {
      return "";
    }
    return body.length() <= 300 ? body : body.substring(0, 300);
  }

  public record NexusInventory(
      List<Map<String, Object>> blobStores,
      List<RepositoryDocument> repositories,
      NexusSecurityExport securityExport,
      List<String> warnings) {

    public NexusInventory {
      blobStores = blobStores == null ? List.of() : List.copyOf(blobStores);
      repositories = repositories == null ? List.of() : List.copyOf(repositories);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  public final class RepositoryDataScriptSession implements AutoCloseable {
    private final String scriptName;
    private boolean closed;

    private RepositoryDataScriptSession(String scriptName) {
      this.scriptName = scriptName;
    }

    public RepositoryAssetPage readPage(String repositoryName, String afterPath, int pageSize)
        throws IOException, InterruptedException {
      return readPage(repositoryName, afterPath, pageSize, null);
    }

    public RepositoryAssetPage readPage(String repositoryName, String afterPath, int pageSize, Instant since)
        throws IOException, InterruptedException {
      if (closed) {
        throw new IllegalStateException("repository data export script session is closed");
      }
      Map<String, Object> request = new LinkedHashMap<>();
      request.put("repositoryName", repositoryName);
      request.put("afterPath", afterPath);
      request.put("pageSize", pageSize);
      if (since != null) {
        request.put("since", since.toString());
      }
      HttpTextResponse run = postText("/service/rest/v1/script/"
          + encodePathSegment(scriptName)
          + "/run", objectMapper.writeValueAsString(request));
      if (!run.success()) {
        throw new IOException("Source Nexus script API did not return repository data page for "
            + repositoryName + ": " + run.describe());
      }
      Map<String, Object> document = objectMapper.readValue(run.body(), MAP);
      String result = string(document.get("result"));
      if (result == null) {
        throw new IOException("Source Nexus script API returned an empty repository data result for "
            + repositoryName);
      }
      return repositoryAssetPageFromScriptResult(result);
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      try {
        delete("/service/rest/v1/script/" + encodePathSegment(scriptName));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while deleting Nexus repository data export script", e);
      }
    }
  }

  private RepositoryAssetPage repositoryAssetPageFromScriptResult(String result) throws IOException {
    Map<String, Object> document = objectMapper.readValue(result, MAP);
    List<RepositoryAssetMetadata> assets = objectMaps(document.get("assets")).stream()
        .map(NexusRestClient::repositoryAssetMetadata)
        .toList();
    return new RepositoryAssetPage(
        string(document.get("repositoryName")),
        string(document.get("afterPath")),
        string(document.get("nextAfterPath")),
        bool(document.get("complete")),
        assets,
        stringList(document.get("warnings")));
  }

  private static RepositoryAssetMetadata repositoryAssetMetadata(Map<String, Object> source) {
    return new RepositoryAssetMetadata(
        firstString(source, "repositoryName"),
        firstString(source, "assetId"),
        firstString(source, "componentId"),
        firstString(source, "path"),
        firstString(source, "format"),
        firstString(source, "namespace"),
        firstString(source, "name"),
        firstString(source, "version"),
        firstString(source, "assetKind"),
        firstString(source, "contentType"),
        longValue(source.get("size")),
        firstString(source, "sourceBlobRef"),
        firstString(source, "lastUpdated"),
        firstString(source, "lastDownloaded"),
        firstString(source, "blobCreated"),
        firstString(source, "blobUpdated"),
        firstString(source, "createdBy"),
        firstString(source, "createdByIp"),
        safeMap(objectValue(source.get("attributes"))),
        safeMap(objectValue(source.get("componentAttributes"))));
  }

  private static boolean bool(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    return value != null && Boolean.parseBoolean(String.valueOf(value));
  }

  private static Long longValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Map<String, Object> objectValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      return objectMap(map);
    }
    return Map.of();
  }

  public record RepositoryAssetPage(
      String repositoryName,
      String afterPath,
      String nextAfterPath,
      boolean complete,
      List<RepositoryAssetMetadata> assets,
      List<String> warnings) {

    public RepositoryAssetPage {
      assets = assets == null ? List.of() : List.copyOf(assets);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  public record RepositoryAssetMetadata(
      String repositoryName,
      String assetId,
      String componentId,
      String path,
      String format,
      String namespace,
      String name,
      String version,
      String assetKind,
      String contentType,
      Long size,
      String sourceBlobRef,
      String lastUpdated,
      String lastDownloaded,
      String blobCreated,
      String blobUpdated,
      String createdBy,
      String createdByIp,
      Map<String, Object> attributes,
      Map<String, Object> componentAttributes) {

    public RepositoryAssetMetadata {
      attributes = safeMap(attributes);
      componentAttributes = safeMap(componentAttributes);
    }
  }

  private record SecurityExportResult(
      NexusSecurityExport export,
      List<String> warnings) {

    private SecurityExportResult {
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  private record SecurityScriptProbe(
      Map<String, String> passwordHashes,
      List<Map<String, Object>> apiKeys,
      List<String> warnings) {

    private SecurityScriptProbe {
      passwordHashes = passwordHashes == null ? Map.of() : Map.copyOf(passwordHashes);
      apiKeys = apiKeys == null ? List.of() : List.copyOf(apiKeys);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    private static SecurityScriptProbe warning(String warning) {
      return new SecurityScriptProbe(Map.of(), List.of(), List.of(warning));
    }
  }

  record HttpTextResponse(
      int statusCode,
      String body) {

    private boolean success() {
      return statusCode >= 200 && statusCode < 300;
    }

    private String describe() {
      return "HTTP " + statusCode + (body == null || body.isBlank() ? "" : ": " + truncate(body));
    }
  }

  @FunctionalInterface
  interface HttpTransport {
    HttpTextResponse send(HttpRequest request) throws IOException, InterruptedException;
  }

  @FunctionalInterface
  private interface RequestFactory {
    HttpRequest build(URI baseUri);
  }

  public record RepositoryDocument(
      Map<String, Object> summary,
      Map<String, Object> detail) {

    public RepositoryDocument {
      summary = safeMap(summary);
      detail = safeMap(detail);
    }
  }

  private static Map<String, Object> safeMap(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> {
      if (key != null && value != null) {
        copy.put(key, value);
      }
    });
    return Map.copyOf(copy);
  }
}
