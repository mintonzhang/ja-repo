package com.github.klboke.nexusplus.server.blob;

import com.github.klboke.nexusplus.core.BlobReference;

/**
 * Persisted blob reference codec.
 *
 * <p>The blob store is selected by {@code asset_blob.blob_store_id}; this string is a stable,
 * human-readable reference to the physical object inside that store. New rows use the storage
 * neutral {@code blob://bucket-or-store/object-key} form. The legacy {@code s3://...} form remains
 * readable for rows written before this codec existed.
 */
public final class BlobReferenceCodec {
  public static final String SCHEME = "blob://";
  private static final String LEGACY_S3_SCHEME = "s3://";

  private BlobReferenceCodec() {
  }

  public static String format(BlobReference reference) {
    if (reference == null) {
      throw new IllegalArgumentException("blob reference is required");
    }
    return SCHEME + nullToEmpty(reference.bucket()) + "/" + nullToEmpty(reference.objectKey());
  }

  public static BlobReference reference(String blobRef, String objectKey, String sha256, long size) {
    return new BlobReference(bucket(blobRef), objectKey(blobRef, objectKey), sha256, size);
  }

  public static String bucket(String blobRef) {
    String rest = rest(blobRef);
    if (rest == null) {
      return "";
    }
    int slash = rest.indexOf('/');
    return slash < 0 ? rest : rest.substring(0, slash);
  }

  public static String objectKey(String blobRef, String fallbackObjectKey) {
    if (fallbackObjectKey != null && !fallbackObjectKey.isBlank()) {
      return fallbackObjectKey;
    }
    String rest = rest(blobRef);
    if (rest == null) {
      return nullToEmpty(fallbackObjectKey);
    }
    int slash = rest.indexOf('/');
    return slash < 0 || slash == rest.length() - 1 ? "" : rest.substring(slash + 1);
  }

  private static String rest(String blobRef) {
    if (blobRef == null || blobRef.isBlank()) {
      return null;
    }
    if (blobRef.startsWith(SCHEME)) {
      return blobRef.substring(SCHEME.length());
    }
    if (blobRef.startsWith(LEGACY_S3_SCHEME)) {
      return blobRef.substring(LEGACY_S3_SCHEME.length());
    }
    return null;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
