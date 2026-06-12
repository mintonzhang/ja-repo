package com.github.klboke.nexusplus.server.http;

import com.github.klboke.nexusplus.core.BlobRangeReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.http.HttpHeaders;

/**
 * Shared single-range partial fetch support for repository content routes.
 */
public final class SingleRangePartialFetchSupport<T> {
  public T apply(
      String method,
      String rangeHeader,
      String ifRangeHeader,
      T response,
      ResponseAdapter<T> adapter) {
    if (!"GET".equals(method) || adapter.status(response) != 200 || !adapter.hasBody(response)
        || adapter.contentLength(response) <= 0 || rangeHeader == null) {
      return response;
    }
    Range range = parse(rangeHeader, adapter.contentLength(response));
    if (range == Range.WHOLE) {
      return response;
    }
    if (ifRangeHeader != null && !ifRangeMatches(response, ifRangeHeader, adapter)) {
      return response;
    }
    if (range == Range.UNSATISFIABLE) {
      adapter.closeBodyIfOpen(response);
      return adapter.withHeader(
          adapter.noBody(416),
          HttpHeaders.CONTENT_RANGE,
          "bytes */" + adapter.contentLength(response));
    }
    InputStream partialBody = partialBody(adapter.body(response), range.start(), range.length());
    T partial = adapter.withStatus(adapter.ok(
        partialBody,
        range.length(),
        adapter.contentType(response),
        adapter.etag(response),
        adapter.lastModified(response)), 206);
    adapter.headers(response).forEach((name, value) -> adapter.withHeader(partial, name, value));
    return adapter.withHeader(
        partial,
        HttpHeaders.CONTENT_RANGE,
        "bytes " + range.start() + "-" + range.end() + "/" + adapter.contentLength(response));
  }

  private InputStream partialBody(InputStream body, long start, long length) {
    if (body instanceof BlobRangeReader rangeReader) {
      try {
        body.close();
      } catch (IOException ignored) {
      }
      return rangeReader.openRange(start, length);
    }
    return new PartialInputStream(body, start, length);
  }

  private Range parse(String rangeHeader, long size) {
    if (rangeHeader == null || rangeHeader.isBlank()) {
      return Range.WHOLE;
    }
    try {
      if (!rangeHeader.startsWith("bytes=") || rangeHeader.length() <= 6 || rangeHeader.contains(",")) {
        return Range.WHOLE;
      }
      String spec = rangeHeader.substring(6);
      if (spec.startsWith("-")) {
        long byteCount = Long.parseLong(spec.substring(1));
        if (byteCount > size) {
          return Range.UNSATISFIABLE;
        }
        return satisfiable(size - byteCount, size - 1, size);
      }
      if (spec.endsWith("-")) {
        long start = Long.parseLong(spec.substring(0, spec.length() - 1));
        return satisfiable(start, size - 1, size);
      }
      int dash = spec.indexOf('-');
      if (dash >= 0) {
        long start = Long.parseLong(spec.substring(0, dash));
        long end = Long.parseLong(spec.substring(dash + 1));
        return satisfiable(start, end, size);
      }
    } catch (RuntimeException ignored) {
      return Range.WHOLE;
    }
    return Range.WHOLE;
  }

  private Range satisfiable(long requestedStart, long requestedEnd, long size) {
    long contentStart = 0;
    long contentEnd = size - 1;
    if (requestedStart > contentEnd || requestedEnd < contentStart || requestedStart > requestedEnd) {
      return Range.UNSATISFIABLE;
    }
    return new Range(Math.max(requestedStart, contentStart), Math.min(requestedEnd, contentEnd));
  }

  private boolean ifRangeMatches(T response, String ifRangeHeader, ResponseAdapter<T> adapter) {
    if (ifRangeHeader.startsWith("\"")) {
      return adapter.etag(response) != null && ifRangeHeader.equals("\"" + adapter.etag(response) + "\"");
    }
    if (adapter.lastModified(response) == null) {
      return false;
    }
    Instant requested = parseHttpDate(ifRangeHeader);
    if (requested == null) {
      return false;
    }
    return requested.equals(parseHttpDate(formatHttpDate(adapter.lastModified(response))));
  }

  private static Instant parseHttpDate(String value) {
    try {
      return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String formatHttpDate(Instant instant) {
    return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
  }

  public interface ResponseAdapter<T> {
    int status(T response);

    boolean hasBody(T response);

    InputStream body(T response);

    long contentLength(T response);

    String contentType(T response);

    String etag(T response);

    Instant lastModified(T response);

    Map<String, String> headers(T response);

    T ok(InputStream body, long contentLength, String contentType, String etag, Instant lastModified);

    T noBody(int status);

    T withStatus(T response, int status);

    T withHeader(T response, String name, String value);

    default void closeBodyIfOpen(T response) {
    }
  }

  private record Range(long start, long end) {
    private static final Range WHOLE = new Range(-1, -1);
    private static final Range UNSATISFIABLE = new Range(-2, -2);

    long length() {
      return end - start + 1;
    }
  }

  private static final class PartialInputStream extends InputStream {
    private final InputStream delegate;
    private final long start;
    private long remaining;
    private boolean skipped;

    private PartialInputStream(InputStream delegate, long start, long length) {
      this.delegate = delegate;
      this.start = start;
      this.remaining = length;
      this.skipped = start == 0;
    }

    @Override
    public int read() throws IOException {
      ensureSkipped();
      if (remaining <= 0) {
        return -1;
      }
      int value = delegate.read();
      if (value >= 0) {
        remaining--;
      }
      return value;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      ensureSkipped();
      if (remaining <= 0) {
        return -1;
      }
      int allowed = (int) Math.min(len, remaining);
      int read = delegate.read(b, off, allowed);
      if (read > 0) {
        remaining -= read;
      }
      return read;
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }

    private void ensureSkipped() throws IOException {
      if (skipped) {
        return;
      }
      long left = start;
      while (left > 0) {
        long skippedNow = delegate.skip(left);
        if (skippedNow <= 0) {
          if (delegate.read() < 0) {
            break;
          }
          skippedNow = 1;
        }
        left -= skippedNow;
      }
      skipped = true;
    }
  }
}
