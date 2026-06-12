package com.github.klboke.nexusplus.server.maven;

import com.github.klboke.nexusplus.cache.SharedCache;
import com.github.klboke.nexusplus.protocol.maven.path.MavenPath;
import com.github.klboke.nexusplus.server.metrics.NexusPlusMetrics;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Shared proxy-path 404 cache, matching Nexus' NegativeCacheHandler shape.
 *
 * <p>The cache is scoped to a concrete proxy repository and path. Group repositories still probe
 * members in order; only a proxy member that has already seen a 404 for the same path can
 * short-circuit the remote call.
 */
@Service
public class ProxyNegativeCache {
  private static final String NAMESPACE = "proxy-negative-cache";
  private static final String NOT_FOUND = "404";

  private final SharedCache cache;
  private final NexusPlusMetrics metrics;
  private final boolean enabled;
  private final Duration ttl;

  public ProxyNegativeCache(
      SharedCache cache,
      @Value("${nexus-plus.proxy.negative-cache.enabled:${nexus-plus.maven.proxy-negative-cache.enabled:true}}")
      boolean enabled,
      @Value("${nexus-plus.proxy.negative-cache.ttl-minutes:${nexus-plus.maven.proxy-negative-cache.ttl-minutes:5}}")
      long ttlMinutes) {
    this(cache, enabled, ttlMinutes, null);
  }

  @Autowired
  public ProxyNegativeCache(
      SharedCache cache,
      @Value("${nexus-plus.proxy.negative-cache.enabled:${nexus-plus.maven.proxy-negative-cache.enabled:true}}")
      boolean enabled,
      @Value("${nexus-plus.proxy.negative-cache.ttl-minutes:${nexus-plus.maven.proxy-negative-cache.ttl-minutes:5}}")
      long ttlMinutes,
      NexusPlusMetrics metrics) {
    this.cache = cache;
    this.metrics = metrics;
    this.enabled = enabled;
    this.ttl = Duration.ofMinutes(Math.max(0, ttlMinutes));
  }

  public boolean isNotFoundCached(RepositoryRuntime runtime, MavenPath path) {
    return isNotFoundCached(runtime, path.path());
  }

  public boolean isNotFoundCached(RepositoryRuntime runtime, String path) {
    if (!enabled || ttl.isZero() || !runtime.isProxy()) {
      return false;
    }
    boolean cached = cache.getString(NAMESPACE, key(runtime, path)).map(NOT_FOUND::equals).orElse(false);
    record(runtime, cached ? "negative_hit" : "negative_miss");
    return cached;
  }

  public void rememberNotFound(RepositoryRuntime runtime, MavenPath path) {
    rememberNotFound(runtime, path.path());
  }

  public void rememberNotFound(RepositoryRuntime runtime, String path) {
    if (!enabled || ttl.isZero() || !runtime.isProxy()) {
      return;
    }
    cache.putString(NAMESPACE, key(runtime, path), NOT_FOUND, ttl);
    record(runtime, "negative_store");
  }

  public void invalidate(RepositoryRuntime runtime, MavenPath path) {
    invalidate(runtime, path.path());
  }

  public void invalidate(RepositoryRuntime runtime, String path) {
    if (!runtime.isProxy()) {
      return;
    }
    cache.evict(NAMESPACE, key(runtime, path));
    record(runtime, "negative_invalidate");
  }

  public void invalidateRepository(long repositoryId) {
    cache.evictByPrefix(NAMESPACE, repositoryId + ":");
  }

  private static String key(RepositoryRuntime runtime, String path) {
    return runtime.id() + ":" + path;
  }

  private void record(RepositoryRuntime runtime, String result) {
    if (metrics != null) {
      metrics.recordProxyCacheEvent(runtime, result);
    }
  }
}
