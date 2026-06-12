package com.github.klboke.nexusplus.storage.file.admin;

import com.github.klboke.nexusplus.core.BlobReference;
import com.github.klboke.nexusplus.core.BlobStorage;
import com.github.klboke.nexusplus.storage.file.FileBlobStoreConfig;
import com.github.klboke.nexusplus.storage.file.FileBlobStorePathValidator;
import com.github.klboke.nexusplus.storage.file.FileBlobStorageFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class FileBlobStoreAdmin {
  private final FileBlobStorePathValidator pathValidator;
  private final FileBlobStorageFactory storageFactory;

  public FileBlobStoreAdmin(
      FileBlobStorePathValidator pathValidator,
      FileBlobStorageFactory storageFactory) {
    this.pathValidator = pathValidator;
    this.storageFactory = storageFactory;
  }

  public FileBlobStoreSummary summary(FileBlobStoreConfig config) {
    try {
      String localSemantics = "File path is reachable; distributed deployments must mount a strong-consistency shared filesystem";
      if (!Files.exists(config.root())) {
        return new FileBlobStoreSummary(
            config.name(),
            config.path(),
            config.root().toString(),
            "Missing",
            false,
            0,
            0,
            "Directory does not exist");
      }
      if (!Files.isDirectory(config.root())) {
        return new FileBlobStoreSummary(
            config.name(),
            config.path(),
            config.root().toString(),
            "Unavailable",
            false,
            0,
            0,
            "Path is not a directory");
      }
      if (!Files.isWritable(config.root())) {
        return new FileBlobStoreSummary(
            config.name(),
            config.path(),
            config.root().toString(),
            "Unavailable",
            true,
            0,
            0,
            "Directory is not writable");
      }
      return new FileBlobStoreSummary(
          config.name(),
          config.path(),
          config.root().toString(),
          "Started",
          true,
          0,
          0,
          localSemantics);
    } catch (RuntimeException e) {
      return new FileBlobStoreSummary(
          config.name(),
          config.path(),
          config.root().toString(),
          "Unavailable",
          false,
          0,
          0,
          e.getMessage());
    }
  }

  public FileProbeResult probeReadWrite(FileBlobStoreConfig config) {
    pathValidator.validateWritableDirectory(config.root());
    FileBlobStoreSummary current = summary(config);
    if (!current.exists()) {
      return new FileProbeResult(false, "", current.message(), current);
    }
    BlobStorage blobStorage = storageFactory.forStore(config);
    String payload = "nexus-plus-file-probe " + Instant.now();
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    BlobReference reference = blobStorage.put(
        "_system",
        "probe-" + System.currentTimeMillis() + ".txt",
        new ByteArrayInputStream(bytes),
        bytes.length,
        "");
    try (var stream = blobStorage.get(reference).orElseThrow()) {
      String actual = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      if (!payload.equals(actual)) {
        return new FileProbeResult(
            false,
            reference.objectKey(),
            "Read content does not match written content",
            summary(config));
      }
      return new FileProbeResult(true, reference.objectKey(), "Read/write probe succeeded", summary(config));
    } catch (Exception e) {
      return new FileProbeResult(false, reference.objectKey(), e.getMessage(), summary(config));
    } finally {
      blobStorage.delete(reference);
    }
  }

  public long cleanupTemporaryFiles(FileBlobStoreConfig config, Instant olderThan) {
    if (olderThan == null || !Files.isDirectory(config.root())) {
      return 0;
    }
    try (Stream<Path> paths = Files.walk(config.root())) {
      return paths
          .filter(Files::isRegularFile)
          .filter(FileBlobStoreAdmin::isTemporaryBlobFile)
          .filter(path -> lastModifiedBefore(path, olderThan))
          .mapToLong(path -> deleteIfExists(path) ? 1 : 0)
          .sum();
    } catch (Exception e) {
      return 0;
    }
  }

  private static boolean isTemporaryBlobFile(Path path) {
    Path name = path.getFileName();
    if (name == null) {
      return false;
    }
    String fileName = name.toString();
    return fileName.endsWith(".tmp") || fileName.contains(".tmp-") || path.toString().contains("/.tmp/");
  }

  private static boolean lastModifiedBefore(Path path, Instant olderThan) {
    try {
      return Files.getLastModifiedTime(path).toInstant().isBefore(olderThan);
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean deleteIfExists(Path path) {
    try {
      return Files.deleteIfExists(path);
    } catch (Exception e) {
      return false;
    }
  }

  public record FileBlobStoreSummary(
      String name,
      String path,
      String resolvedPath,
      String state,
      boolean exists,
      long objectCount,
      long totalSize,
      String message) {
  }

  public record FileProbeResult(
      boolean ok,
      String objectKey,
      String message,
      FileBlobStoreSummary summary) {
  }
}
