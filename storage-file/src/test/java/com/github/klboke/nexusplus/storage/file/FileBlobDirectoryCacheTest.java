package com.github.klboke.nexusplus.storage.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FileBlobDirectoryCacheTest {
  @Test
  void skipsCreateDirectoriesForKnownDirectory() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    FileBlobDirectoryCache cache = new FileBlobDirectoryCache(directory -> {
      calls.incrementAndGet();
      return directory;
    });
    Path directory = Path.of("/blob/root/repositories/maven/blobs/v2/aa/bb");

    cache.ensureExists(directory);
    cache.ensureExists(directory);

    assertEquals(1, calls.get());
  }

  @Test
  void failedCreateDoesNotPoisonCache() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    FileBlobDirectoryCache cache = new FileBlobDirectoryCache(directory -> {
      if (calls.incrementAndGet() == 1) {
        throw new IOException("temporary nfs failure");
      }
      return directory;
    });
    Path directory = Path.of("/blob/root/repositories/maven/blobs/v2/aa/bb");

    assertThrows(IOException.class, () -> cache.ensureExists(directory));
    cache.ensureExists(directory);

    assertEquals(2, calls.get());
  }
}
