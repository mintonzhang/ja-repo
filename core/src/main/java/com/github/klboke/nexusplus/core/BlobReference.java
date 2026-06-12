package com.github.klboke.nexusplus.core;

public record BlobReference(
    String bucket,
    String objectKey,
    String sha256,
    long size) {
}
