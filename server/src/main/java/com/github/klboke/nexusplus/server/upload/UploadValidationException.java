package com.github.klboke.nexusplus.server.upload;

public class UploadValidationException extends RuntimeException {
  public UploadValidationException(String message) {
    super(message);
  }

  public UploadValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
