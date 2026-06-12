package com.github.klboke.nexusplus.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.klboke.nexusplus.auth.PermissionSubject;
import com.github.klboke.nexusplus.server.support.InMemorySharedCache;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ApiKeyAuthCacheTest {

  @Test
  void positiveHitSkipsLoaderOnSecondCall() {
    InMemorySharedCache shared = new InMemorySharedCache();
    ApiKeyAuthCache cache = new ApiKeyAuthCache(shared, true, 60, 5);

    AtomicInteger loads = new AtomicInteger();
    Optional<AuthenticatedSubject> first = cache.find("token-A", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "alice", 99L));
    });
    Optional<AuthenticatedSubject> second = cache.find("token-A", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "alice", 99L));
    });

    assertEquals(1, loads.get(), "second lookup must be served from cache");
    assertTrue(first.isPresent());
    assertTrue(second.isPresent());
    assertEquals(first.get().userId(), second.get().userId());
    assertEquals(first.get().apiKeyId(), second.get().apiKeyId());
    assertEquals(
        first.get().permissionSubject().groupIds(),
        second.get().permissionSubject().groupIds());
  }

  @Test
  void negativeHitIsCachedAcrossCalls() {
    InMemorySharedCache shared = new InMemorySharedCache();
    ApiKeyAuthCache cache = new ApiKeyAuthCache(shared, true, 60, 60);

    AtomicInteger loads = new AtomicInteger();
    Optional<AuthenticatedSubject> first = cache.find("nope", () -> {
      loads.incrementAndGet();
      return Optional.empty();
    });
    Optional<AuthenticatedSubject> second = cache.find("nope", () -> {
      loads.incrementAndGet();
      return Optional.empty();
    });

    assertEquals(1, loads.get(), "negative miss must be cached to short-circuit brute-force probing");
    assertFalse(first.isPresent());
    assertFalse(second.isPresent());
  }

  @Test
  void disabledFlagBypassesCache() {
    InMemorySharedCache shared = new InMemorySharedCache();
    ApiKeyAuthCache cache = new ApiKeyAuthCache(shared, false, 60, 5);

    AtomicInteger loads = new AtomicInteger();
    Optional<AuthenticatedSubject> a = cache.find("t", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "u", 1L));
    });
    Optional<AuthenticatedSubject> b = cache.find("t", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "u", 1L));
    });

    assertEquals(2, loads.get(), "disabled cache must defer to loader every call");
    assertTrue(a.isPresent());
    assertTrue(b.isPresent());
  }

  @Test
  void evictForcesReloadOnNextCall() {
    InMemorySharedCache shared = new InMemorySharedCache();
    ApiKeyAuthCache cache = new ApiKeyAuthCache(shared, true, 60, 5);

    AtomicInteger loads = new AtomicInteger();
    cache.find("t", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "u", 1L));
    });
    cache.evict("t");
    cache.find("t", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "u", 1L));
    });

    assertEquals(2, loads.get(), "evict must invalidate the entry");
  }

  @Test
  void evictAllClearsEveryEntry() {
    InMemorySharedCache shared = new InMemorySharedCache();
    ApiKeyAuthCache cache = new ApiKeyAuthCache(shared, true, 60, 5);

    AtomicInteger loads = new AtomicInteger();
    cache.find("t-1", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "alice", 1L));
    });
    cache.find("t-2", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "bob", 2L));
    });
    cache.evictAll();
    cache.find("t-1", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "alice", 1L));
    });
    cache.find("t-2", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "bob", 2L));
    });

    assertEquals(4, loads.get(), "evictAll must wipe every cached token");
  }

  @Test
  void nullOrEmptyTokenAlwaysDelegatesToLoader() {
    InMemorySharedCache shared = new InMemorySharedCache();
    ApiKeyAuthCache cache = new ApiKeyAuthCache(shared, true, 60, 5);

    AtomicInteger loads = new AtomicInteger();
    cache.find(null, () -> {
      loads.incrementAndGet();
      return Optional.empty();
    });
    cache.find("", () -> {
      loads.incrementAndGet();
      return Optional.empty();
    });

    assertEquals(2, loads.get());
  }

  @Test
  void zeroPositiveTtlPreventsPositiveCaching() {
    InMemorySharedCache shared = new InMemorySharedCache();
    ApiKeyAuthCache cache = new ApiKeyAuthCache(shared, true, 0, 60);

    AtomicInteger loads = new AtomicInteger();
    cache.find("t", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "u", 1L));
    });
    cache.find("t", () -> {
      loads.incrementAndGet();
      return Optional.of(subject("Local", "u", 1L));
    });

    assertEquals(2, loads.get(), "positive ttl=0 must not cache hits");
  }

  @Test
  void cacheReadFailureFallsBackToLoader() {
    FailingReadCache shared = new FailingReadCache();
    ApiKeyAuthCache cache = new ApiKeyAuthCache(shared, true, 60, 5);

    AuthenticatedSubject expected = subject("Local", "u", 1L);
    Optional<AuthenticatedSubject> result = cache.find("t", () -> Optional.of(expected));

    assertTrue(result.isPresent());
    assertSame(expected, result.get());
  }

  private static AuthenticatedSubject subject(String source, String userId, long apiKeyId) {
    PermissionSubject permission = new PermissionSubject(
        source, userId, Set.of("nx-admin", "nx-anonymous"), String.valueOf(apiKeyId));
    return new AuthenticatedSubject(source, userId, "api-key", apiKeyId, permission);
  }

  private static class FailingReadCache extends InMemorySharedCache {
    @Override
    public Optional<String> getString(String namespace, String key) {
      throw new IllegalStateException("simulated cache read failure");
    }

    @Override
    public void putString(String namespace, String key, String value, Duration ttl) {
      // ignore — we don't care what the cache does on the write side here
    }
  }
}
