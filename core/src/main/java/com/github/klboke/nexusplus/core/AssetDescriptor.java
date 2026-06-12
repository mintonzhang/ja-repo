package com.github.klboke.nexusplus.core;

public record AssetDescriptor(
    String repository,
    String logicalPath,
    PackageCoordinate coordinate,
    BlobReference blob,
    String contentType) {
}
