package com.github.klboke.nexusplus.server.pypi;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class PypiResponse {
  private final int status;
  private final InputStream body;
  private final BodySupplier bodySupplier;
  private final long contentLength;
  private final String contentType;
  private final String etag;
  private final Instant lastModified;
  private final Map<String, String> headers;

  private PypiResponse(int status, InputStream body, long contentLength, String contentType,
      String etag, Instant lastModified, Map<String, String> headers) {
    this(status, body, null, contentLength, contentType, etag, lastModified, headers);
  }

  private PypiResponse(int status, InputStream body, BodySupplier bodySupplier, long contentLength, String contentType,
      String etag, Instant lastModified, Map<String, String> headers) {
    this.status = status;
    this.body = body;
    this.bodySupplier = bodySupplier;
    this.contentLength = contentLength;
    this.contentType = contentType;
    this.etag = etag;
    this.lastModified = lastModified;
    this.headers = headers;
  }

  public static PypiResponse ok(InputStream body, long contentLength, String contentType,
      String etag, Instant lastModified) {
    return new PypiResponse(200, body, contentLength, contentType, etag, lastModified, new HashMap<>());
  }

  public static PypiResponse ok(BodySupplier bodySupplier, long contentLength, String contentType,
      String etag, Instant lastModified) {
    return new PypiResponse(200, null, bodySupplier, contentLength, contentType, etag, lastModified, new HashMap<>());
  }

  public static PypiResponse noBody(int status) {
    return new PypiResponse(status, null, 0, null, null, null, new HashMap<>());
  }

  public static PypiResponse noBody(int status, long contentLength, String contentType,
      String etag, Instant lastModified) {
    return new PypiResponse(status, null, contentLength, contentType, etag, lastModified, new HashMap<>());
  }

  public PypiResponse withStatus(int status) {
    return new PypiResponse(status, body, bodySupplier, contentLength, contentType, etag, lastModified, headers);
  }

  public PypiResponse withHeader(String name, String value) {
    headers.put(name, value);
    return this;
  }

  void closeBodyIfOpen() {
    if (body == null) {
      return;
    }
    try {
      body.close();
    } catch (IOException ignored) {
    }
  }

  public int status() { return status; }
  public boolean hasBody() { return body != null || bodySupplier != null; }
  public InputStream body() { return body != null || bodySupplier == null ? body : bodySupplier.open(); }
  public long contentLength() { return contentLength; }
  public String contentType() { return contentType; }
  public String etag() { return etag; }
  public Instant lastModified() { return lastModified; }
  public Map<String, String> headers() { return headers; }

  @FunctionalInterface
  public interface BodySupplier {
    InputStream open();
  }
}
