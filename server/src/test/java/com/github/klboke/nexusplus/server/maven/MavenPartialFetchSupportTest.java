package com.github.klboke.nexusplus.server.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.klboke.nexusplus.core.BlobRangeReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class MavenPartialFetchSupportTest {
  private final MavenPartialFetchSupport support = new MavenPartialFetchSupport();

  @Test
  void singleRangeReturnsPartialContent() throws IOException {
    MavenResponse response = support.apply("GET", "bytes=2-4", null, response("abcdef"));

    assertEquals(206, response.status());
    assertEquals(3, response.contentLength());
    assertEquals("bytes 2-4/6", response.headers().get(HttpHeaders.CONTENT_RANGE));
    assertEquals("cde", body(response));
  }

  @Test
  void suffixRangeLargerThanPayloadIsUnsatisfiableLikeNexus() {
    MavenResponse response = support.apply("GET", "bytes=-10", null, response("abcdef"));

    assertEquals(416, response.status());
    assertEquals("bytes */6", response.headers().get(HttpHeaders.CONTENT_RANGE));
    assertNull(response.body());
  }

  @Test
  void malformedAndMultipleRangesFallBackToWholeResponse() throws IOException {
    MavenResponse malformed = support.apply("GET", "bytes=abc", null, response("abcdef"));
    MavenResponse multiple = support.apply("GET", "bytes=0-1,3-4", null, response("abcdef"));

    assertEquals(200, malformed.status());
    assertEquals("abcdef", body(malformed));
    assertEquals(200, multiple.status());
    assertEquals("abcdef", body(multiple));
  }

  @Test
  void ifRangeMismatchFallsBackToWholeResponse() throws IOException {
    MavenResponse mismatch = support.apply("GET", "bytes=0-1", "\"other\"", response("abcdef"));
    MavenResponse match = support.apply("GET", "bytes=0-1", "\"sha1\"", response("abcdef"));

    assertEquals(200, mismatch.status());
    assertEquals("abcdef", body(mismatch));
    assertEquals(206, match.status());
    assertEquals("ab", body(match));
  }

  @Test
  void ifRangeMismatchIgnoresUnsatisfiableRangeAndReturnsWholeResponse() throws IOException {
    MavenResponse response = support.apply("GET", "bytes=10-", "\"other\"", response("abcdef"));

    assertEquals(200, response.status());
    assertEquals("abcdef", body(response));
  }

  @Test
  void rangeDoesNotApplyToHead() throws IOException {
    MavenResponse response = support.apply("HEAD", "bytes=0-1", null, response("abcdef"));

    assertEquals(200, response.status());
    assertEquals("abcdef", body(response));
  }

  @Test
  void rangeReaderBodyOpensDirectRangeAndClosesOriginalBody() throws IOException {
    DirectRangeBody body = new DirectRangeBody("abcdef");
    MavenResponse response = MavenResponse.ok(
        body,
        6,
        "application/octet-stream",
        "sha1",
        Instant.parse("2026-05-28T00:00:00Z"));

    MavenResponse partial = support.apply("GET", "bytes=2-4", null, response);

    assertEquals(206, partial.status());
    assertEquals("cde", body(partial));
    assertTrue(body.closed);
    assertEquals(1, body.openRangeCalls);
  }

  private static MavenResponse response(String body) {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    return MavenResponse.ok(
        new ByteArrayInputStream(bytes),
        bytes.length,
        "application/octet-stream",
        "sha1",
        Instant.parse("2026-05-28T00:00:00Z"));
  }

  private static String body(MavenResponse response) throws IOException {
    return new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
  }

  private static final class DirectRangeBody extends InputStream implements BlobRangeReader {
    private final byte[] bytes;
    private boolean closed;
    private int openRangeCalls;

    private DirectRangeBody(String body) {
      this.bytes = body.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public InputStream openRange(long start, long length) {
      openRangeCalls++;
      return new ByteArrayInputStream(bytes, (int) start, (int) length);
    }

    @Override
    public int read() {
      throw new AssertionError("whole-stream read should not be used for direct range bodies");
    }

    @Override
    public long skip(long n) {
      throw new AssertionError("whole-stream skip should not be used for direct range bodies");
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
