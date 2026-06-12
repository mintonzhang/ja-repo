package com.github.klboke.nexusplus.server.maven;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Marks an I/O failure that happened while reading the upstream HTTP response body.
 */
public final class UpstreamBodyReadException extends RuntimeException {
  public UpstreamBodyReadException(IOException cause) {
    super("Upstream response body read failed: " + message(cause), cause);
  }

  public IOException ioCause() {
    return (IOException) getCause();
  }

  public static InputStream wrap(InputStream delegate) {
    return new FilterInputStream(delegate) {
      @Override
      public int read() {
        try {
          return super.read();
        } catch (IOException e) {
          throw new UpstreamBodyReadException(e);
        }
      }

      @Override
      public int read(byte[] b) {
        try {
          return super.read(b);
        } catch (IOException e) {
          throw new UpstreamBodyReadException(e);
        }
      }

      @Override
      public int read(byte[] b, int off, int len) {
        try {
          return super.read(b, off, len);
        } catch (IOException e) {
          throw new UpstreamBodyReadException(e);
        }
      }
    };
  }

  public static byte[] readAllBytes(InputStream delegate) {
    try {
      return delegate.readAllBytes();
    } catch (IOException e) {
      throw new UpstreamBodyReadException(e);
    }
  }

  private static String message(IOException cause) {
    String message = cause.getMessage();
    return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
  }
}
