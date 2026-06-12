package com.github.klboke.nexusplus.storage.s3;

import com.github.klboke.nexusplus.core.BlobStorage;
import org.springframework.stereotype.Component;

@Component
public class S3BlobStorageFactory {
  private final S3ClientFactory clientFactory;
  private final OssClientFactory ossClientFactory;

  public S3BlobStorageFactory(
      S3ClientFactory clientFactory,
      OssClientFactory ossClientFactory) {
    this.clientFactory = clientFactory;
    this.ossClientFactory = ossClientFactory;
  }

  public BlobStorage forStore(S3BlobStoreConfig config) {
    // Exhaustive over Engine: adding an engine forces a compile error here until it's handled.
    return switch (config.engineType()) {
      case OSS_NATIVE -> new OssNativeBlobStorage(ossClientFactory.client(config), config);
      case AWS_S3 -> new S3BlobStorage(clientFactory.client(config), config);
    };
  }
}
