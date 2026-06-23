package com.github.klboke.kkrepo.server.docker;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DockerTransferLimiter {
  private final int maxUploads;
  private final int maxDownloads;
  private final Semaphore uploadPermits;
  private final Semaphore downloadPermits;
  private final AtomicInteger activeUploads = new AtomicInteger();
  private final AtomicInteger activeDownloads = new AtomicInteger();

  public DockerTransferLimiter(
      @Value("${kkrepo.docker.transfer.max-concurrent-uploads:0}") int maxUploads,
      @Value("${kkrepo.docker.transfer.max-concurrent-downloads:0}") int maxDownloads) {
    this.maxUploads = Math.max(0, maxUploads);
    this.maxDownloads = Math.max(0, maxDownloads);
    this.uploadPermits = this.maxUploads == 0 ? null : new Semaphore(this.maxUploads);
    this.downloadPermits = this.maxDownloads == 0 ? null : new Semaphore(this.maxDownloads);
  }

  public Lease tryAcquire(TransferKind kind) {
    Semaphore semaphore = kind == TransferKind.UPLOAD ? uploadPermits : downloadPermits;
    AtomicInteger counter = kind == TransferKind.UPLOAD ? activeUploads : activeDownloads;
    if (semaphore != null && !semaphore.tryAcquire()) {
      return Lease.rejected(kind);
    }
    counter.incrementAndGet();
    return new Lease(kind, semaphore, counter, true);
  }

  public Snapshot snapshot() {
    return new Snapshot(maxUploads, activeUploads.get(), maxDownloads, activeDownloads.get());
  }

  public enum TransferKind {
    UPLOAD,
    DOWNLOAD
  }

  public static final class Lease implements AutoCloseable {
    private final TransferKind kind;
    private final Semaphore semaphore;
    private final AtomicInteger counter;
    private final boolean acquired;
    private boolean closed;

    private Lease(TransferKind kind, Semaphore semaphore, AtomicInteger counter, boolean acquired) {
      this.kind = kind;
      this.semaphore = semaphore;
      this.counter = counter;
      this.acquired = acquired;
    }

    static Lease rejected(TransferKind kind) {
      return new Lease(kind, null, null, false);
    }

    public boolean acquired() {
      return acquired;
    }

    public TransferKind kind() {
      return kind;
    }

    @Override
    public void close() {
      if (!acquired || closed) {
        return;
      }
      closed = true;
      counter.decrementAndGet();
      if (semaphore != null) {
        semaphore.release();
      }
    }
  }

  public record Snapshot(
      int maxConcurrentUploads,
      int activeUploads,
      int maxConcurrentDownloads,
      int activeDownloads) {
  }
}
