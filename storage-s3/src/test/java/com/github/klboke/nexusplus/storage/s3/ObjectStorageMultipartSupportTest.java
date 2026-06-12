package com.github.klboke.nexusplus.storage.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObjectStorageMultipartSupportTest {
  @TempDir
  Path tempDir;

  @Test
  void partSizeKeepsPartCountWithinObjectStoreLimit() {
    long configured = ObjectStorageMultipartSupport.MIN_PART_SIZE;
    long objectSize = configured * ObjectStorageMultipartSupport.MAX_PARTS + 1;

    long partSize = ObjectStorageMultipartSupport.partSize(objectSize, configured);

    assertTrue(partSize > configured);
    assertTrue(ObjectStorageMultipartSupport.partCount(objectSize, partSize)
        <= ObjectStorageMultipartSupport.MAX_PARTS);
  }

  @Test
  void openFileSliceReadsOnlyRequestedRange() throws Exception {
    Path file = tempDir.resolve("blob.bin");
    Files.writeString(file, "0123456789", StandardCharsets.US_ASCII);

    try (InputStream in = ObjectStorageMultipartSupport.openFileSlice(file, 3, 4)) {
      assertEquals("3456", new String(in.readAllBytes(), StandardCharsets.US_ASCII));
    }
  }

  @Test
  void parallelismHonorsConfiguredConcurrencyConnectionsAndPartCount() {
    S3BlobStoreConfig config = S3BlobStoreConfig.of(
        1,
        "store",
        "http://localhost:9000",
        "us-east-1",
        "bucket",
        "",
        Map.of("maxConnections", 2, "multipartConcurrency", 8));

    assertEquals(2, ObjectStorageMultipartSupport.parallelism(config, 10));
    assertEquals(1, ObjectStorageMultipartSupport.parallelism(config, 1));
    assertEquals(2, ObjectStorageMultipartSupport.storeParallelism(config));
  }

  @Test
  void byteRangeFormatsInclusiveEnd() {
    assertEquals("bytes=10-14", ObjectStorageMultipartSupport.byteRange(10, 5));
  }
}
