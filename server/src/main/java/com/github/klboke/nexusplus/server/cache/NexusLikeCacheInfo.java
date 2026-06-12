package com.github.klboke.nexusplus.server.cache;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record NexusLikeCacheInfo(
    Instant lastVerified,
    String cacheToken,
    NexusCacheType type) {
  public static final String ATTR_CACHE = "cache";
  public static final String ATTR_CACHE_TOKEN = "cache_token";
  public static final String ATTR_LAST_VERIFIED = "last_verified";
  public static final String ATTR_TYPE = "type";
  public static final String INVALIDATED = "invalidated";

  public boolean invalidated() {
    return INVALIDATED.equals(cacheToken);
  }

  public static Optional<NexusLikeCacheInfo> fromAttributes(Map<String, Object> attributes) {
    if (attributes == null) {
      return Optional.empty();
    }
    Object raw = attributes.get(ATTR_CACHE);
    if (!(raw instanceof Map<?, ?> cache)) {
      return Optional.empty();
    }
    Instant lastVerified = instant(cache.get(ATTR_LAST_VERIFIED));
    if (lastVerified == null) {
      return Optional.empty();
    }
    String token = string(cache.get(ATTR_CACHE_TOKEN));
    NexusCacheType type = cacheType(cache.get(ATTR_TYPE));
    return Optional.of(new NexusLikeCacheInfo(lastVerified, token, type));
  }

  public static Map<String, Object> applyToAttributes(
      Map<String, Object> attributes,
      NexusLikeCacheInfo cacheInfo) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (attributes != null) {
      result.putAll(attributes);
    }
    if (cacheInfo == null) {
      return result;
    }
    Map<String, Object> cache = new LinkedHashMap<>();
    cache.put(ATTR_LAST_VERIFIED, cacheInfo.lastVerified() == null ? null : cacheInfo.lastVerified().toString());
    cache.put(ATTR_CACHE_TOKEN, cacheInfo.cacheToken());
    cache.put(ATTR_TYPE, cacheInfo.type() == null ? null : cacheInfo.type().name());
    result.put(ATTR_CACHE, cache);
    return result;
  }

  private static Instant instant(Object raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Instant instant) {
      return instant;
    }
    try {
      return Instant.parse(raw.toString());
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static NexusCacheType cacheType(Object raw) {
    if (raw == null) {
      return null;
    }
    try {
      return NexusCacheType.valueOf(raw.toString());
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static String string(Object raw) {
    return raw == null ? null : raw.toString();
  }
}
