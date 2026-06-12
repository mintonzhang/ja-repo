package com.github.klboke.nexusplus.protocol.nuget;

public record NugetPath(
    Kind kind,
    String rawPath,
    String packageId,
    String version) {
  public enum Kind {
    SERVICE_INDEX,
    QUERY,
    AUTOCOMPLETE,
    REGISTRATION_INDEX,
    PACKAGE_PUBLISH,
    PACKAGE_DELETE,
    FLAT_CONTAINER_VERSION_INDEX,
    FLAT_CONTAINER_PACKAGE,
    FLAT_CONTAINER_NUSPEC,
    RAW
  }
}
