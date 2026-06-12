package com.github.klboke.nexusplus.protocol.maven.path;

public enum HashType {
  SHA1("sha1", "SHA-1"),
  SHA256("sha256", "SHA-256"),
  SHA512("sha512", "SHA-512"),
  MD5("md5", "MD5");

  private final String ext;
  private final String javaAlgorithm;

  HashType(String ext, String javaAlgorithm) {
    this.ext = ext;
    this.javaAlgorithm = javaAlgorithm;
  }

  public String ext() {
    return ext;
  }

  public String javaAlgorithm() {
    return javaAlgorithm;
  }
}
