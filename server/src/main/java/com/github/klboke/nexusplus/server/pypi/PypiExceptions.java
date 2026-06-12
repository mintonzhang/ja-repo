package com.github.klboke.nexusplus.server.pypi;

public final class PypiExceptions {
  private PypiExceptions() {
  }

  public static class PypiNotFoundException extends RuntimeException {
    public PypiNotFoundException(String message) {
      super(message);
    }
  }

  public static class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
      super(message);
    }
  }

  public static class WritePolicyDenied extends RuntimeException {
    public WritePolicyDenied(String message) {
      super(message);
    }
  }

  public static class MethodNotAllowed extends RuntimeException {
    public MethodNotAllowed(String message) {
      super(message);
    }
  }

  public static class BadUpstreamException extends RuntimeException {
    public BadUpstreamException(String message) {
      super(message);
    }

    public BadUpstreamException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
