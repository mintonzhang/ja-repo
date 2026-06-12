package com.github.klboke.nexusplus.server.security;

public class SecurityValidationException extends RuntimeException {
  public SecurityValidationException(String message) {
    super(message);
  }

  public SecurityValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
