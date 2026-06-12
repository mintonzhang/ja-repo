package com.github.klboke.nexusplus.storage.file;

import com.github.klboke.nexusplus.core.BlobStorage;
import com.github.klboke.nexusplus.storage.file.config.FileStorageProperties;
import org.springframework.stereotype.Component;

@Component
public class FileBlobStorageFactory {
  private final FileStorageProperties properties;

  public FileBlobStorageFactory(FileStorageProperties properties) {
    this.properties = properties;
  }

  public BlobStorage forStore(FileBlobStoreConfig config) {
    return new FileBlobStorage(config);
  }

  public FileStorageProperties properties() {
    return properties;
  }

  public FileBlobStoreConfig configFor(long id, String name, String path) {
    return new FileBlobStoreConfig(id, name, path, properties.resolvePath(path));
  }
}
