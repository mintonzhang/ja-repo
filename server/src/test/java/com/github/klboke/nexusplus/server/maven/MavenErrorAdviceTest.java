package com.github.klboke.nexusplus.server.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MavenErrorAdviceTest {

  @Test
  void notFoundBodyIncludesHttpStatus() {
    MavenErrorAdvice advice = new MavenErrorAdvice();

    ResponseEntity<Map<String, Object>> response = advice.notFound(
        new MavenExceptions.MavenNotFoundException("io/sentry/sentry-logback/6.9.1/sentry-logback-6.9.1.module"));

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals(404, response.getBody().get("status"));
    assertEquals("Not Found", response.getBody().get("error"));
    assertEquals("io/sentry/sentry-logback/6.9.1/sentry-logback-6.9.1.module",
        response.getBody().get("message"));
  }
}
