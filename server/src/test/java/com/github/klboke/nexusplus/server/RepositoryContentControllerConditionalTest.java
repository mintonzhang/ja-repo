package com.github.klboke.nexusplus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryType;
import com.github.klboke.nexusplus.persistence.mysql.dao.RepositoryDao;
import com.github.klboke.nexusplus.persistence.mysql.model.RepositoryRecord;
import com.github.klboke.nexusplus.protocol.maven.path.MavenPath;
import com.github.klboke.nexusplus.server.maven.MavenHostedService;
import com.github.klboke.nexusplus.server.maven.MavenResponse;
import com.github.klboke.nexusplus.server.maven.RepositoryRuntime;
import com.github.klboke.nexusplus.server.maven.RepositoryRuntimeRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class RepositoryContentControllerConditionalTest {
  @Test
  void mavenRangeWithMatchingIfNoneMatchReturnsNotModifiedBeforeOpeningBody() {
    CountingHostedService hosted = new CountingHostedService();
    RepositoryContentController controller = controller(hosted);
    MockHttpServletRequest request = new MockHttpServletRequest(
        "GET", "/repository/maven/com/acme/app/1.0/app-1.0.jar");
    request.addHeader(HttpHeaders.RANGE, "bytes=1-3");
    request.addHeader(HttpHeaders.IF_NONE_MATCH, "\"sha1\"");

    ResponseEntity<StreamingResponseBody> response = controller.get("maven", request);

    assertEquals(304, response.getStatusCode().value());
    assertNull(response.getBody());
    assertEquals(0, hosted.openBodyCalls);
  }

  @Test
  void mavenRangeStillReturnsPartialBodyAfterConditionalMiss() throws Exception {
    CountingHostedService hosted = new CountingHostedService();
    RepositoryContentController controller = controller(hosted);
    MockHttpServletRequest request = new MockHttpServletRequest(
        "GET", "/repository/maven/com/acme/app/1.0/app-1.0.jar");
    request.addHeader(HttpHeaders.RANGE, "bytes=1-3");
    request.addHeader(HttpHeaders.IF_NONE_MATCH, "\"other\"");

    ResponseEntity<StreamingResponseBody> response = controller.get("maven", request);
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    response.getBody().writeTo(body);

    assertEquals(206, response.getStatusCode().value());
    assertEquals("bcd", body.toString(StandardCharsets.UTF_8));
    assertEquals(1, hosted.openBodyCalls);
  }

  private static RepositoryContentController controller(MavenHostedService hosted) {
    FakeRepositoryDao repositories = new FakeRepositoryDao();
    repositories.repository(repository("maven", RepositoryFormat.MAVEN2, RepositoryType.HOSTED));
    return new RepositoryContentController(
        new RepositoryRuntimeRegistry(repositories, 0),
        hosted, null, null,
        null, null,
        null, null,
        null,
        null, null, null,
        null, null,
        null, null, null,
        null, null, null,
        new ObjectMapper(),
        null);
  }

  private static RepositoryRecord repository(String name, RepositoryFormat format, RepositoryType type) {
    return new RepositoryRecord(
        1L,
        name,
        format,
        type,
        format.name().toLowerCase() + "-" + type.name().toLowerCase(),
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

  private static final class CountingHostedService extends MavenHostedService {
    private int openBodyCalls;

    CountingHostedService() {
      super(null, null, null, null, null, null, false);
    }

    @Override
    public MavenResponse get(RepositoryRuntime runtime, MavenPath path, boolean headOnly) {
      byte[] bytes = "abcdef".getBytes(StandardCharsets.UTF_8);
      if (headOnly) {
        return MavenResponse.noBody(200, bytes.length, "application/octet-stream",
            "sha1", Instant.parse("2026-06-08T00:00:00Z"));
      }
      return MavenResponse.ok(
          () -> {
            openBodyCalls++;
            return new ByteArrayInputStream(bytes);
          },
          bytes.length,
          "application/octet-stream",
          "sha1",
          Instant.parse("2026-06-08T00:00:00Z"));
    }
  }

  private static final class FakeRepositoryDao extends RepositoryDao {
    private RepositoryRecord repository;

    FakeRepositoryDao() {
      super(null, null);
    }

    void repository(RepositoryRecord repository) {
      this.repository = repository;
    }

    @Override
    public Optional<RepositoryRecord> findByName(String name) {
      return repository != null && repository.name().equals(name) ? Optional.of(repository) : Optional.empty();
    }

    @Override
    public List<RepositoryRecord> listMembers(long groupRepositoryId) {
      return List.of();
    }
  }
}
