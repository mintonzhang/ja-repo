package com.github.klboke.nexusplus.server.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class ConditionalResponsesTest {
  @Test
  void ifNoneMatchSupportsWeakAndListTags() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/repository/raw/file.txt");
    request.addHeader(HttpHeaders.IF_NONE_MATCH, "W/\"miss\", \"abc\"");

    assertTrue(ConditionalResponses.shouldReturnNotModified(
        request, 200, "abc", Instant.parse("2026-06-08T10:15:30Z")));
  }

  @Test
  void ifNoneMatchWildcardMatchesCurrentRepresentationWithoutEtag() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/repository/raw/file.txt");
    request.addHeader(HttpHeaders.IF_NONE_MATCH, "*");

    assertTrue(ConditionalResponses.shouldReturnNotModified(
        request, 200, null, Instant.parse("2026-06-08T10:15:30Z")));
  }

  @Test
  void ifNoneMatchTakesPrecedenceOverIfModifiedSince() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/repository/raw/file.txt");
    request.addHeader(HttpHeaders.IF_NONE_MATCH, "\"new\"");
    request.addHeader(HttpHeaders.IF_MODIFIED_SINCE, httpDate(Instant.parse("2026-06-08T10:20:00Z")));

    assertFalse(ConditionalResponses.shouldReturnNotModified(
        request, 200, "old", Instant.parse("2026-06-08T10:15:30Z")));
  }

  @Test
  void ifModifiedSinceMatchesSecondPrecision() {
    MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/repository/raw/file.txt");
    request.addHeader(HttpHeaders.IF_MODIFIED_SINCE, httpDate(Instant.parse("2026-06-08T10:15:30Z")));

    assertTrue(ConditionalResponses.shouldReturnNotModified(
        request, 200, "abc", Instant.parse("2026-06-08T10:15:30.900Z")));
  }

  @Test
  void unsafeOrNonOkResponseDoesNotReturnNotModified() {
    MockHttpServletRequest post = new MockHttpServletRequest("POST", "/repository/raw/file.txt");
    post.addHeader(HttpHeaders.IF_NONE_MATCH, "\"abc\"");
    assertFalse(ConditionalResponses.shouldReturnNotModified(
        post, 200, "abc", Instant.parse("2026-06-08T10:15:30Z")));

    MockHttpServletRequest get = new MockHttpServletRequest("GET", "/repository/raw/file.txt");
    get.addHeader(HttpHeaders.IF_NONE_MATCH, "\"abc\"");
    assertFalse(ConditionalResponses.shouldReturnNotModified(
        get, 206, "abc", Instant.parse("2026-06-08T10:15:30Z")));
  }

  private static String httpDate(Instant instant) {
    return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
  }
}
