package com.github.klboke.nexusplus.storage.s3.admin;

public record S3BucketSummary(
    String name,
    String endpoint,
    String region,
    String bucket,
    String state,
    boolean bucketExists,
    long objectCount,
    long totalSize,
    String message) {
}
