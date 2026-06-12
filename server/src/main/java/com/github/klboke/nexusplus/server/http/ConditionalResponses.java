package com.github.klboke.nexusplus.server.http;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public final class ConditionalResponses {
  private ConditionalResponses() {
  }

  public static boolean shouldReturnNotModified(
      HttpServletRequest request, int status, String etag, Instant lastModified) {
    if (request == null || status != HttpStatus.OK.value() || !isConditionalMethod(request.getMethod())) {
      return false;
    }
    String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
    if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
      return etagMatches(ifNoneMatch, etag);
    }
    if (lastModified == null) {
      return false;
    }
    long ifModifiedSince = ifModifiedSince(request);
    if (ifModifiedSince < 0) {
      return false;
    }
    Instant requested = Instant.ofEpochMilli(ifModifiedSince).truncatedTo(ChronoUnit.SECONDS);
    return !lastModified.truncatedTo(ChronoUnit.SECONDS).isAfter(requested);
  }

  public static void addValidators(HttpHeaders headers, String etag, Instant lastModified) {
    if (etag != null) {
      headers.setETag(formatEtag(etag));
    }
    if (lastModified != null) {
      headers.setLastModified(lastModified);
    }
  }

  public static void closeQuietly(InputStream body) {
    if (body == null) {
      return;
    }
    try {
      body.close();
    } catch (IOException ignored) {
    }
  }

  private static boolean isConditionalMethod(String method) {
    return "GET".equals(method) || "HEAD".equals(method);
  }

  private static long ifModifiedSince(HttpServletRequest request) {
    try {
      return request.getDateHeader(HttpHeaders.IF_MODIFIED_SINCE);
    } catch (IllegalArgumentException ignored) {
      return -1;
    }
  }

  private static boolean etagMatches(String ifNoneMatch, String etag) {
    String expected = normalizeEtag(etag);
    for (String candidate : ifNoneMatch.split(",")) {
      String normalized = normalizeEtag(candidate);
      if ("*".equals(normalized) || (!expected.isEmpty() && expected.equals(normalized))) {
        return true;
      }
    }
    return false;
  }

  private static String formatEtag(String etag) {
    String trimmed = etag.trim();
    if (trimmed.startsWith("\"") || trimmed.startsWith("W/\"") || trimmed.startsWith("w/\"")) {
      return trimmed;
    }
    return "\"" + trimmed + "\"";
  }

  private static String normalizeEtag(String etag) {
    if (etag == null) {
      return "";
    }
    String normalized = etag.trim();
    if (normalized.startsWith("W/") || normalized.startsWith("w/")) {
      normalized = normalized.substring(2).trim();
    }
    if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
      normalized = normalized.substring(1, normalized.length() - 1);
    }
    return normalized;
  }
}
