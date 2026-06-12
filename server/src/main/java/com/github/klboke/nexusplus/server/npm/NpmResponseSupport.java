package com.github.klboke.nexusplus.server.npm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klboke.nexusplus.server.maven.MavenResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

final class NpmResponseSupport {
  static final String JSON = "application/json";
  static final String TARBALL = "application/x-tgz";

  private NpmResponseSupport() {
  }

  static MavenResponse json(ObjectMapper mapper, Object value) {
    return json(mapper, 200, value);
  }

  static MavenResponse json(ObjectMapper mapper, int status, Object value) {
    byte[] bytes = write(mapper, value);
    return MavenResponse.ok(new ByteArrayInputStream(bytes), bytes.length, JSON, null, Instant.now())
        .withStatus(status);
  }

  static MavenResponse success(ObjectMapper mapper) {
    return json(mapper, Map.of());
  }

  static byte[] write(ObjectMapper mapper, Object value) {
    try {
      return mapper.writeValueAsBytes(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize npm JSON", e);
    }
  }

  static byte[] errorBytes(String message) {
    String safe = message == null ? "" : message.replace("\"", "\\\"");
    return ("{\"success\":false,\"error\":\"" + safe + "\"}").getBytes(StandardCharsets.UTF_8);
  }
}
