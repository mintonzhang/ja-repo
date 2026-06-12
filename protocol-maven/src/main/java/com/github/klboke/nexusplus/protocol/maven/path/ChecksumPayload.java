package com.github.klboke.nexusplus.protocol.maven.path;

import java.nio.charset.StandardCharsets;

public final class ChecksumPayload {
  private ChecksumPayload() {}

  /** Matches Nexus's generated checksum sidecar format: the hex digest only. */
  public static byte[] format(String hex) {
    return hex.getBytes(StandardCharsets.UTF_8);
  }
}
