package com.github.klboke.nexusplus.storage.s3;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class ObjectStorageMultipartSupport {
  static final long MIN_PART_SIZE = 5L * 1024 * 1024;
  static final int MAX_PARTS = 10_000;

  private ObjectStorageMultipartSupport() {
  }

  static long partSize(long objectSize, long configuredPartSize) {
    long safeConfigured = Math.max(MIN_PART_SIZE, configuredPartSize);
    if (objectSize <= 0) {
      return safeConfigured;
    }
    long minimumForPartLimit = objectSize / MAX_PARTS + (objectSize % MAX_PARTS == 0 ? 0 : 1);
    return Math.max(safeConfigured, minimumForPartLimit);
  }

  static int partCount(long objectSize, long partSize) {
    if (objectSize <= 0) {
      return 0;
    }
    long count = objectSize / partSize + (objectSize % partSize == 0 ? 0 : 1);
    if (count > MAX_PARTS) {
      throw new IllegalArgumentException("Multipart upload exceeds " + MAX_PARTS + " parts");
    }
    return (int) count;
  }

  static int parallelism(S3BlobStoreConfig config, int partCount) {
    return Math.max(1, Math.min(storeParallelism(config), Math.max(1, partCount)));
  }

  static int storeParallelism(S3BlobStoreConfig config) {
    int configured = Math.max(1, config.multipartConcurrency());
    int connectionBound = Math.max(1, config.maxConnections());
    return Math.max(1, Math.min(configured, connectionBound));
  }

  static ExecutorService executor(String storeName, int parallelism) {
    AtomicInteger counter = new AtomicInteger(1);
    ThreadFactory factory = task -> {
      Thread thread = new Thread(task,
          "nexus-plus-object-multipart-" + safeThreadName(storeName) + "-" + counter.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    };
    return Executors.newFixedThreadPool(parallelism, factory);
  }

  static InputStream openFileSlice(Path file, long offset, long length) {
    try {
      FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
      channel.position(offset);
      return new BoundedInputStream(Channels.newInputStream(channel), length);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to open file slice for object upload: " + file, e);
    }
  }

  static String byteRange(long start, long length) {
    if (start < 0 || length <= 0) {
      throw new IllegalArgumentException("range start must be non-negative and length must be positive");
    }
    long end;
    try {
      end = Math.addExact(start, length - 1);
    } catch (ArithmeticException e) {
      end = Long.MAX_VALUE;
    }
    return "bytes=" + start + "-" + end;
  }

  private static String safeThreadName(String value) {
    if (value == null || value.isBlank()) {
      return "store";
    }
    StringBuilder safe = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      safe.append(Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' ? ch : '-');
    }
    return safe.toString();
  }

  private static final class BoundedInputStream extends InputStream {
    private final InputStream delegate;
    private long remaining;

    private BoundedInputStream(InputStream delegate, long length) {
      this.delegate = delegate;
      this.remaining = length;
    }

    @Override
    public int read() throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      int value = delegate.read();
      if (value >= 0) {
        remaining--;
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      int read = delegate.read(buffer, offset, (int) Math.min(length, remaining));
      if (read > 0) {
        remaining -= read;
      }
      return read;
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
