package com.github.klboke.nexusplus.storage.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class FileBlobDirectoryCache {
  private final Set<Path> knownDirectories = ConcurrentHashMap.newKeySet();
  private final DirectoryCreator creator;

  FileBlobDirectoryCache() {
    this(Files::createDirectories);
  }

  FileBlobDirectoryCache(DirectoryCreator creator) {
    this.creator = creator;
  }

  void ensureExists(Path directory) throws IOException {
    if (directory == null || !knownDirectories.add(directory)) {
      return;
    }
    try {
      creator.createDirectories(directory);
    } catch (IOException | RuntimeException e) {
      knownDirectories.remove(directory);
      throw e;
    }
  }

  void forget(Path directory) {
    if (directory != null) {
      knownDirectories.remove(directory);
    }
  }

  @FunctionalInterface
  interface DirectoryCreator {
    Path createDirectories(Path directory) throws IOException;
  }
}
