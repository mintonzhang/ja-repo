package com.github.klboke.nexusplus.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Scheduler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Node-local {@link SharedCache} implementation.
 *
 * <p>Entries are performance caches only: every value has either an explicit TTL or is recoverable
 * from durable MySQL/blob state. Multiple replicas do not share this map; correctness-sensitive
 * coordination must use MySQL-backed mechanisms outside this class.
 */
public class InMemorySharedCache implements SharedCache {
  private static final char KEY_SEPARATOR = '\u0000';

  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final Cache<String, Entry> values;

  public InMemorySharedCache(ObjectMapper objectMapper, long maximumSize, MeterRegistry meterRegistry) {
    this(objectMapper, maximumSize, meterRegistry, Clock.systemUTC());
  }

  InMemorySharedCache(ObjectMapper objectMapper, long maximumSize, MeterRegistry meterRegistry, Clock clock) {
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.clock = clock == null ? Clock.systemUTC() : clock;
    this.values = Caffeine.newBuilder()
        .maximumSize(Math.max(1, maximumSize))
        .scheduler(Scheduler.systemScheduler())
        .expireAfter(new Expiry<String, Entry>() {
          @Override
          public long expireAfterCreate(String key, Entry value, long currentTime) {
            return remainingNanos(value, InMemorySharedCache.this.now());
          }

          @Override
          public long expireAfterUpdate(String key, Entry value, long currentTime, long currentDuration) {
            return remainingNanos(value, InMemorySharedCache.this.now());
          }

          @Override
          public long expireAfterRead(String key, Entry value, long currentTime, long currentDuration) {
            return remainingNanos(value, InMemorySharedCache.this.now());
          }
        })
        .build();
  }

  @Override
  public Optional<String> getString(String namespace, String key) {
    long start = System.nanoTime();
    String cacheKey = cacheKey(namespace, key);
    try {
      Entry entry = values.getIfPresent(cacheKey);
      if (entry == null || entry.expired(now())) {
        values.invalidate(cacheKey);
        record(namespace, "get", "miss", start);
        return Optional.empty();
      }
      record(namespace, "get", "hit", start);
      return Optional.of(entry.value());
    } catch (RuntimeException e) {
      record(namespace, "get", "error", start);
      throw e;
    }
  }

  @Override
  public void putString(String namespace, String key, String value, Duration ttl) {
    long start = System.nanoTime();
    try {
      values.put(cacheKey(namespace, key), new Entry(requiredValue(value), expiresAt(ttl, now())));
      record(namespace, "put", "success", start);
    } catch (RuntimeException e) {
      record(namespace, "put", "error", start);
      throw e;
    }
  }

  @Override
  public Optional<String> getAndDeleteString(String namespace, String key) {
    long start = System.nanoTime();
    String cacheKey = cacheKey(namespace, key);
    try {
      Entry removed = values.asMap().remove(cacheKey);
      if (removed == null || removed.expired(now())) {
        record(namespace, "get_and_delete", "miss", start);
        return Optional.empty();
      }
      record(namespace, "get_and_delete", "hit", start);
      return Optional.of(removed.value());
    } catch (RuntimeException e) {
      record(namespace, "get_and_delete", "error", start);
      throw e;
    }
  }

  @Override
  public void evict(String namespace, String key) {
    long start = System.nanoTime();
    try {
      values.invalidate(cacheKey(namespace, key));
      record(namespace, "evict", "success", start);
    } catch (RuntimeException e) {
      record(namespace, "evict", "error", start);
      throw e;
    }
  }

  @Override
  public void evictByPrefix(String namespace, String keyPrefix) {
    long start = System.nanoTime();
    String prefix = cachePrefix(namespace, keyPrefix);
    try {
      long before = values.estimatedSize();
      values.asMap().keySet().removeIf(key -> key.startsWith(prefix));
      long deleted = Math.max(0, before - values.estimatedSize());
      record(namespace, "evict_by_prefix", "success", start);
      recordDeletedKeys(namespace, deleted);
    } catch (RuntimeException e) {
      record(namespace, "evict_by_prefix", "error", start);
      throw e;
    }
  }

  @Override
  public long increment(String namespace, String key, Duration ttl) {
    long start = System.nanoTime();
    String cacheKey = cacheKey(namespace, key);
    Instant now = now();
    try {
      Entry updated = values.asMap().compute(cacheKey, (ignored, existing) -> {
        if (existing == null || existing.expired(now)) {
          return new Entry("1", expiresAt(ttl, now));
        }
        long next = parseCounter(namespace, key, existing.value()) + 1;
        return new Entry(Long.toString(next), existing.expiresAt());
      });
      long value = updated == null ? 0 : parseCounter(namespace, key, updated.value());
      record(namespace, "increment", "success", start);
      return value;
    } catch (RuntimeException e) {
      record(namespace, "increment", "error", start);
      throw e;
    }
  }

  @Override
  public <T> Optional<T> getJson(String namespace, String key, Class<T> type) {
    return getString(namespace, key).map(value -> readJson(value, type));
  }

  @Override
  public <T> Optional<T> getJson(String namespace, String key, TypeReference<T> type) {
    return getString(namespace, key).map(value -> readJson(value, type));
  }

  @Override
  public void putJson(String namespace, String key, Object value, Duration ttl) {
    try {
      putString(namespace, key, objectMapper.writeValueAsString(value), ttl);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed serializing cache value for " + namespace + "/" + key, e);
    }
  }

  private <T> T readJson(String value, Class<T> type) {
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed deserializing cache value as " + type.getName(), e);
    }
  }

  private <T> T readJson(String value, TypeReference<T> type) {
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed deserializing cache value", e);
    }
  }

  private Instant now() {
    return clock.instant();
  }

  private static Instant expiresAt(Duration ttl, Instant now) {
    return ttl == null || ttl.isZero() || ttl.isNegative() ? null : now.plus(ttl);
  }

  private static long remainingNanos(Entry entry, Instant now) {
    if (entry.expiresAt() == null) {
      return Long.MAX_VALUE;
    }
    Duration remaining = Duration.between(now, entry.expiresAt());
    if (remaining.isZero() || remaining.isNegative()) {
      return 0;
    }
    try {
      return remaining.toNanos();
    } catch (ArithmeticException e) {
      return Long.MAX_VALUE;
    }
  }

  private static long parseCounter(String namespace, String key, String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Cache value for " + namespace + "/" + key + " is not a counter", e);
    }
  }

  private static String cacheKey(String namespace, String key) {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("Cache namespace is required");
    }
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Cache key is required");
    }
    return namespace.trim() + KEY_SEPARATOR + key;
  }

  private static String cachePrefix(String namespace, String keyPrefix) {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("Cache namespace is required");
    }
    return namespace.trim() + KEY_SEPARATOR + (keyPrefix == null ? "" : keyPrefix);
  }

  private static String requiredValue(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Cache value is required");
    }
    return value;
  }

  private void record(String namespace, String operation, String result, long startNanos) {
    if (meterRegistry == null) {
      return;
    }
    Tags tags = Tags.of(
        "namespace", tag(namespace),
        "op", tag(operation),
        "result", tag(result));
    Counter.builder("nexus_plus_cache_requests_total")
        .description("Shared cache operations by namespace")
        .tags(tags)
        .register(meterRegistry)
        .increment();
    Timer.builder("nexus_plus_cache_operation_duration")
        .description("Shared cache operation duration")
        .tags(tags)
        .publishPercentileHistogram()
        .register(meterRegistry)
        .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
  }

  private void recordDeletedKeys(String namespace, long deleted) {
    if (meterRegistry == null || deleted <= 0) {
      return;
    }
    Counter.builder("nexus_plus_cache_scan_deleted_keys_total")
        .description("Shared cache keys deleted by prefix scans")
        .tags("namespace", tag(namespace))
        .register(meterRegistry)
        .increment(deleted);
  }

  private static String tag(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private record Entry(String value, Instant expiresAt) {
    boolean expired(Instant now) {
      return expiresAt != null && !expiresAt.isAfter(now);
    }
  }
}
