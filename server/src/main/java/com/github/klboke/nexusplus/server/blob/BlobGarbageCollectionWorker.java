package com.github.klboke.nexusplus.server.blob;

import com.github.klboke.nexusplus.core.BlobReference;
import com.github.klboke.nexusplus.core.BlobStorage;
import com.github.klboke.nexusplus.persistence.mysql.dao.AssetDao;
import com.github.klboke.nexusplus.persistence.mysql.dao.AssetDao.BlobReconcileWindow;
import com.github.klboke.nexusplus.persistence.mysql.dao.MaintenanceCursorDao;
import com.github.klboke.nexusplus.persistence.mysql.model.AssetBlobRecord;
import com.github.klboke.nexusplus.server.maven.BlobStorageRegistry;
import com.github.klboke.nexusplus.server.metrics.NexusPlusMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class BlobGarbageCollectionWorker {
  private static final Logger log = LoggerFactory.getLogger(BlobGarbageCollectionWorker.class);

  private final AssetDao assetDao;
  private final MaintenanceCursorDao maintenanceCursorDao;
  private final BlobStorageRegistry blobStorageRegistry;
  private final TransactionTemplate transactionTemplate;
  private final NexusPlusMetrics metrics;
  private final boolean enabled;
  private final int batchSize;
  private final int reconcileScanBatchSize;
  private final int reconcileMarkBatchSize;
  private final long deletedGraceSeconds;
  private final long claimRetrySeconds;

  BlobGarbageCollectionWorker(
      AssetDao assetDao,
      MaintenanceCursorDao maintenanceCursorDao,
      BlobStorageRegistry blobStorageRegistry,
      PlatformTransactionManager transactionManager,
      @Value("${nexus-plus.blob-gc.enabled:true}") boolean enabled,
      @Value("${nexus-plus.blob-gc.batch-size:64}") int batchSize,
      @Value("${nexus-plus.blob-gc.reconcile-scan-batch-size:1024}") int reconcileScanBatchSize,
      @Value("${nexus-plus.blob-gc.reconcile-mark-batch-size:${nexus-plus.blob-gc.reconcile-batch-size:256}}")
          int reconcileMarkBatchSize,
      @Value("${nexus-plus.blob-gc.deleted-grace-seconds:3600}") long deletedGraceSeconds,
      @Value("${nexus-plus.blob-gc.claim-retry-seconds:600}") long claimRetrySeconds,
      NexusPlusMetrics metrics) {
    this.assetDao = assetDao;
    this.maintenanceCursorDao = maintenanceCursorDao;
    this.blobStorageRegistry = blobStorageRegistry;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.metrics = metrics;
    this.enabled = enabled;
    this.batchSize = batchSize;
    this.reconcileScanBatchSize = reconcileScanBatchSize;
    this.reconcileMarkBatchSize = reconcileMarkBatchSize;
    this.deletedGraceSeconds = deletedGraceSeconds;
    this.claimRetrySeconds = claimRetrySeconds;
  }

  @Scheduled(fixedDelayString = "${nexus-plus.blob-gc.poll-interval-ms:30000}")
  public void drain() {
    if (!enabled) return;
    Timer.Sample batchSample = metrics.startTimer();
    try {
      reconcileUnreferencedBlobs();
      collectSoftDeletedBlobs();
      metrics.recordWorkerBatch("blob_gc", "success", batchSample);
    } catch (RuntimeException e) {
      metrics.recordWorkerBatch("blob_gc", "error", batchSample);
      log.warn("blob GC batch failed; will retry next cycle", e);
    }
  }

  private void reconcileUnreferencedBlobs() {
    BlobReconcileWindow window = transactionTemplate.execute(status -> {
      OptionalLong lastSeenId = maintenanceCursorDao.tryLockLastSeenId(
          MaintenanceCursorDao.BLOB_UNREFERENCED_RECONCILE);
      if (lastSeenId.isEmpty()) return null;
      BlobReconcileWindow result = assetDao.markUnreferencedBlobsDeletedAfter(
          lastSeenId.getAsLong(),
          reconcileScanBatchSize,
          reconcileMarkBatchSize,
          "unreferenced blob reconcile");
      maintenanceCursorDao.updateLastSeenId(
          MaintenanceCursorDao.BLOB_UNREFERENCED_RECONCILE,
          result.nextLastSeenId());
      return result;
    });
    if (window == null) {
      log.debug("blob orphan reconcile cursor is locked by another worker");
      metrics.incrementWorkerItems("blob_gc", "reconcile", "locked", 1);
      return;
    }
    metrics.incrementWorkerItems("blob_gc", "reconcile", "success", 1);
    metrics.incrementBlobGcReconcile(window.scanned(), window.marked());
    if (window.marked() > 0) {
      log.info("marked {} unreferenced blobs for GC after scanning {} blob rows",
          window.marked(), window.scanned());
    } else if (window.wrapped()) {
      log.debug("blob orphan reconcile cursor wrapped to beginning");
    }
  }

  private void collectSoftDeletedBlobs() {
    Instant now = Instant.now();
    Instant deletedBefore = now.minusSeconds(Math.max(0, deletedGraceSeconds));
    Instant claimRetryBefore = now.minusSeconds(Math.max(1, claimRetrySeconds));
    List<AssetBlobRecord> blobs = transactionTemplate.execute(status ->
        assetDao.claimDeletedBlobsForGc(batchSize, deletedBefore, claimRetryBefore));
    if (blobs == null || blobs.isEmpty()) return;
    metrics.incrementWorkerItems("blob_gc", "deleted_blob", "claimed", blobs.size());
    for (AssetBlobRecord blob : blobs) {
      collectOne(blob);
    }
  }

  private void collectOne(AssetBlobRecord blob) {
    Timer.Sample itemSample = metrics.startTimer();
    try {
      BlobStorage storage = blobStorageRegistry.forBlobStoreId(blob.blobStoreId());
      long[] deletedBytes = {0};
      transactionTemplate.executeWithoutResult(status -> {
        assetDao.lockDeletedBlobById(blob.id()).ifPresent(locked -> {
          if (!assetDao.hasLiveBlobForObjectKeyHash(locked.blobStoreId(), locked.objectKeyHash())) {
            storage.delete(BlobReferenceCodec.reference(
                locked.blobRef(), locked.objectKey(), locked.sha256(), locked.size()));
            deletedBytes[0] = Math.max(0, locked.size());
          }
          assetDao.hardDeleteBlobByIdIfDeleted(locked.id());
        });
      });
      metrics.incrementBlobGcDeletedBytes(deletedBytes[0]);
      metrics.recordWorkerItem("blob_gc", "deleted_blob", "success", itemSample);
    } catch (RuntimeException e) {
      metrics.recordWorkerItem("blob_gc", "deleted_blob", "error", itemSample);
      log.warn("blob GC failed for blobId={} objectKey={}", blob.id(), blob.objectKey(), e);
      transactionTemplate.executeWithoutResult(status -> assetDao.releaseBlobGcClaim(blob.id()));
    }
  }

}
