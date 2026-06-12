package com.github.klboke.nexusplus.protocol.maven.path;

public record Coordinates(
    boolean snapshot,
    String groupId,
    String artifactId,
    String version,
    Long timestamp,
    Integer buildNumber,
    String baseVersion,
    String classifier,
    String extension,
    SignatureType signatureType) {
}
