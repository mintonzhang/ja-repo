package com.github.klboke.nexusplus.server.security;

import com.github.klboke.nexusplus.cache.SharedCache;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Caches resolved {@link AuthenticatedSubject} entries keyed by the SHA-256 hash of the presented
 * API key token.
 *
 * <p>Every authenticated request that arrives with an API key today triggers a candidate-by-
 * candidate {@code SELECT FROM api_key WHERE token_hash = ?} chain, a {@code SELECT FROM
 * security_user}, a {@code SELECT FROM user_role}, and an {@code UPDATE api_key SET last_used_at}.
 * Under CI/CD-style traffic this dominates MySQL load on the auth path. A short-TTL node-local
 * cache collapses the steady-state into a memory lookup on each replica.
 *
 * <p>No explicit invalidation hook is exposed for now — TTL is the safety net. With a 60-second
 * positive TTL, API-key revocation and role changes propagate within a minute, which matches the
 * SLA we already accept for {@link AuthenticatedSubject} reuse via HTTP session.
 */
@Service
public class ApiKeyAuthCache {
  private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthCache.class);
  private static final String NAMESPACE = "api-key-auth";
  private static final String MISSING_MARKER = "__missing__";

  private final SharedCache sharedCache;
  private final boolean enabled;
  private final Duration positiveTtl;
  private final Duration negativeTtl;

  public ApiKeyAuthCache(
      SharedCache sharedCache,
      @Value("${nexus-plus.cache.api-key.enabled:true}") boolean enabled,
      @Value("${nexus-plus.cache.api-key.ttl-seconds:60}") long ttlSeconds,
      @Value("${nexus-plus.cache.api-key.missing-ttl-seconds:5}") long missingTtlSeconds) {
    this.sharedCache = sharedCache;
    this.enabled = enabled;
    this.positiveTtl = ttlSeconds <= 0 ? Duration.ZERO : Duration.ofSeconds(ttlSeconds);
    this.negativeTtl = missingTtlSeconds <= 0 ? Duration.ZERO : Duration.ofSeconds(missingTtlSeconds);
  }

  /**
   * Read-through. On miss, invokes the loader and caches its result (positive or negative).
   * Returns {@link Optional#empty()} when the token is invalid or maps to a disabled
   * user/api-key.
   */
  public Optional<AuthenticatedSubject> find(
      String presentedToken,
      Supplier<Optional<AuthenticatedSubject>> loader) {
    if (!enabled || presentedToken == null || presentedToken.isEmpty()) {
      return loader.get();
    }
    String key = SecurityHashing.sha256(presentedToken);
    try {
      Optional<String> raw = sharedCache.getString(NAMESPACE, key);
      if (raw.isPresent()) {
        if (MISSING_MARKER.equals(raw.get())) {
          return Optional.empty();
        }
        Optional<AuthenticatedSubject> cached =
            sharedCache.getJson(NAMESPACE, key, AuthenticatedSubject.class);
        if (cached.isPresent()) {
          return cached;
        }
      }
    } catch (RuntimeException e) {
      log.warn("Failed reading api-key auth cache, falling back to MySQL", e);
      return loader.get();
    }

    Optional<AuthenticatedSubject> loaded = loader.get();
    try {
      if (loaded.isEmpty()) {
        if (!negativeTtl.isZero()) {
          sharedCache.putString(NAMESPACE, key, MISSING_MARKER, negativeTtl);
        }
      } else if (!positiveTtl.isZero()) {
        sharedCache.putJson(NAMESPACE, key, loaded.get(), positiveTtl);
      }
    } catch (RuntimeException e) {
      log.warn("Failed writing api-key auth cache", e);
    }
    return loaded;
  }

  /** Evict the cached entry for {@code presentedToken}. Safe to call from any thread. */
  public void evict(String presentedToken) {
    if (!enabled || presentedToken == null || presentedToken.isEmpty()) {
      return;
    }
    try {
      sharedCache.evict(NAMESPACE, SecurityHashing.sha256(presentedToken));
    } catch (RuntimeException e) {
      log.warn("Failed evicting api-key auth cache", e);
    }
  }

  /**
   * Evict every cached entry. Use when an admin revokes API keys in bulk or rotates user roles
   * for many users at once.
   */
  public void evictAll() {
    if (!enabled) {
      return;
    }
    try {
      sharedCache.evictByPrefix(NAMESPACE, "");
    } catch (RuntimeException e) {
      log.warn("Failed bulk-evicting api-key auth cache", e);
    }
  }
}
