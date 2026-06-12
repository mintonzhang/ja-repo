package com.github.klboke.nexusplus.server.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.klboke.nexusplus.core.BlobObjectMetadata;
import com.github.klboke.nexusplus.core.BlobReference;
import com.github.klboke.nexusplus.core.BlobStorage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InstrumentedBlobStorageTest {

  @Test
  void recordsBlobStorageOperationOutcomesAndBytes() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    InstrumentedBlobStorage storage = new InstrumentedBlobStorage(
        new StubBlobStorage(),
        new NexusPlusMetrics(registry),
        "default",
        "s3",
        "aws_s3");
    BlobReference reference = storage.put(
        "maven-releases",
        "com/acme/app.jar",
        new ByteArrayInputStream(new byte[]{1, 2, 3}),
        3,
        "sha256");

    Optional<InputStream> found = storage.get(reference);
    Optional<InputStream> missing = storage.get(new BlobReference("bucket", "missing", "sha256", 9));
    storage.delete(reference);

    assertEquals(true, found.isPresent());
    assertEquals(true, missing.isEmpty());
    assertCounter(registry, "put", "success", 1.0);
    assertCounter(registry, "get", "success", 1.0);
    assertCounter(registry, "get", "miss", 1.0);
    assertCounter(registry, "delete", "success", 1.0);
    var bytes = registry.find("nexus_plus_blob_storage_bytes_total")
        .tags("store", "default", "type", "s3", "engine", "aws_s3", "op", "put")
        .counter();
    assertNotNull(bytes);
    assertEquals(3.0, bytes.count());
  }

  @Test
  void delegatesStagingDirectory() {
    Path stagingDirectory = Path.of("/tmp/nexus-plus-test-staging");
    InstrumentedBlobStorage storage = new InstrumentedBlobStorage(
        new StubBlobStorage(stagingDirectory),
        new NexusPlusMetrics(new SimpleMeterRegistry()),
        "default",
        "file",
        "file");

    assertEquals(Optional.of(stagingDirectory), storage.stagingDirectory());
  }

  private static void assertCounter(
      SimpleMeterRegistry registry,
      String operation,
      String outcome,
      double expected) {
    var counter = registry.find("nexus_plus_blob_storage_operations_total")
        .tags(
            "store", "default",
            "type", "s3",
            "engine", "aws_s3",
            "op", operation,
            "outcome", outcome)
        .counter();
    assertNotNull(counter);
    assertEquals(expected, counter.count());
  }

  private static final class StubBlobStorage implements BlobStorage {
    private final Path stagingDirectory;

    private StubBlobStorage() {
      this(null);
    }

    private StubBlobStorage(Path stagingDirectory) {
      this.stagingDirectory = stagingDirectory;
    }

    @Override
    public BlobReference put(String repository, String logicalPath, InputStream content, long size, String sha256) {
      return new BlobReference("bucket", "present", sha256, size);
    }

    @Override
    public Optional<Path> stagingDirectory() {
      return Optional.ofNullable(stagingDirectory);
    }

    @Override
    public Optional<InputStream> get(BlobReference reference) {
      if ("present".equals(reference.objectKey())) {
        return Optional.of(new ByteArrayInputStream(new byte[]{1, 2, 3}));
      }
      return Optional.empty();
    }

    @Override
    public boolean exists(BlobReference reference) {
      return "present".equals(reference.objectKey());
    }

    @Override
    public Optional<BlobObjectMetadata> stat(BlobReference reference) {
      return Optional.empty();
    }

    @Override
    public void delete(BlobReference reference) {
    }
  }
}
