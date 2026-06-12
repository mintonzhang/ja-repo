package com.github.klboke.nexusplus.server.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.klboke.nexusplus.core.BlobReference;
import org.junit.jupiter.api.Test;

class BlobReferenceCodecTest {

  @Test
  void formatsNewStorageNeutralBlobRef() {
    BlobReference reference = new BlobReference(
        "default",
        "repositories/maven-central/blobs/v2/a1/ec/object-id",
        "sha256",
        123L);

    assertEquals(
        "blob://default/repositories/maven-central/blobs/v2/a1/ec/object-id",
        BlobReferenceCodec.format(reference));
  }

  @Test
  void parsesNewBlobRef() {
    BlobReference reference = BlobReferenceCodec.reference(
        "blob://default/repositories/maven-central/blobs/v2/a1/ec/object-id",
        null,
        "sha256",
        123L);

    assertEquals("default", reference.bucket());
    assertEquals("repositories/maven-central/blobs/v2/a1/ec/object-id", reference.objectKey());
  }

  @Test
  void parsesLegacyS3BlobRef() {
    BlobReference reference = BlobReferenceCodec.reference(
        "s3://default/repositories/maven-central/blobs/v2/a1/ec/object-id",
        "repositories/maven-central/blobs/v2/a1/ec/object-id",
        "sha256",
        123L);

    assertEquals("default", reference.bucket());
    assertEquals("repositories/maven-central/blobs/v2/a1/ec/object-id", reference.objectKey());
  }
}
