package com.github.klboke.nexusplus.server.blob;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class BlobTransferConfiguration {
  BlobTransferConfiguration(
      @Value("${nexus-plus.blob.transfer-buffer-size-bytes:1048576}") int transferBufferSizeBytes) {
    TempBlobFiles.configureResponseBufferSize(transferBufferSizeBytes);
  }
}
