package com.github.klboke.nexusplus.server.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.klboke.nexusplus.server.support.InMemoryVersionWatermark;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MysqlCatalogCacheBroadcasterTest {
  @Test
  void subscribeStartsAtCurrentVersionAndPublishSuppressesLocalPollReload() {
    InMemoryVersionWatermark watermark = new InMemoryVersionWatermark();
    watermark.bump("catalog:security");
    MysqlCatalogCacheBroadcaster broadcaster = new MysqlCatalogCacheBroadcaster(watermark);
    AtomicInteger refreshes = new AtomicInteger();

    broadcaster.subscribe("security", refreshes::incrementAndGet);
    broadcaster.poll();
    broadcaster.publishRefresh("security");
    broadcaster.poll();

    assertEquals(0, refreshes.get());
  }

  @Test
  void externalVersionBumpTriggersListenerOnce() {
    InMemoryVersionWatermark watermark = new InMemoryVersionWatermark();
    MysqlCatalogCacheBroadcaster broadcaster = new MysqlCatalogCacheBroadcaster(watermark);
    AtomicInteger refreshes = new AtomicInteger();
    broadcaster.subscribe("blob-store", refreshes::incrementAndGet);

    watermark.bump("catalog:blob-store");
    broadcaster.poll();
    broadcaster.poll();

    assertEquals(1, refreshes.get());
  }

  @Test
  void failedRefreshDoesNotAdvanceLastSeen() {
    InMemoryVersionWatermark watermark = new InMemoryVersionWatermark();
    MysqlCatalogCacheBroadcaster broadcaster = new MysqlCatalogCacheBroadcaster(watermark);
    AtomicInteger attempts = new AtomicInteger();
    broadcaster.subscribe("security", () -> {
      if (attempts.incrementAndGet() == 1) {
        throw new IllegalStateException("temporary");
      }
    });

    watermark.bump("catalog:security");
    broadcaster.poll();
    broadcaster.poll();

    assertEquals(2, attempts.get());
  }

  @Test
  void blankCatalogNameIsRejected() {
    MysqlCatalogCacheBroadcaster broadcaster = new MysqlCatalogCacheBroadcaster(new InMemoryVersionWatermark());

    assertThrows(IllegalArgumentException.class, () -> broadcaster.publishRefresh(" "));
  }
}
