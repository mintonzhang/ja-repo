package com.github.klboke.nexusplus.protocol.maven.path;

public enum SignatureType {
  GPG("asc");

  private final String ext;

  SignatureType(String ext) {
    this.ext = ext;
  }

  public String ext() {
    return ext;
  }
}
