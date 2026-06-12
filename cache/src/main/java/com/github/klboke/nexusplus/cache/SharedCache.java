package com.github.klboke.nexusplus.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.Optional;

public interface SharedCache {
  Optional<String> getString(String namespace, String key);

  void putString(String namespace, String key, String value, Duration ttl);

  Optional<String> getAndDeleteString(String namespace, String key);

  void evict(String namespace, String key);

  void evictByPrefix(String namespace, String keyPrefix);

  long increment(String namespace, String key, Duration ttl);

  <T> Optional<T> getJson(String namespace, String key, Class<T> type);

  <T> Optional<T> getJson(String namespace, String key, TypeReference<T> type);

  void putJson(String namespace, String key, Object value, Duration ttl);
}
