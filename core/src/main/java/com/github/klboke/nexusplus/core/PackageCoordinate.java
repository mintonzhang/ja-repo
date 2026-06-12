package com.github.klboke.nexusplus.core;

public record PackageCoordinate(
    RepositoryFormat format,
    String namespace,
    String name,
    String version) {
}
