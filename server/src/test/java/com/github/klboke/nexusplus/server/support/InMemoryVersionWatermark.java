package com.github.klboke.nexusplus.server.support;

import com.github.klboke.nexusplus.server.cache.VersionWatermark;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryVersionWatermark implements VersionWatermark {
  private final Map<String, Long> versions = new ConcurrentHashMap<>();

  @Override
  public long bump(String name) {
    return versions.merge(name, 1L, Long::sum);
  }

  @Override
  public long current(String name) {
    return versions.getOrDefault(name, 0L);
  }

  @Override
  public Map<String, Long> currentAll() {
    return new LinkedHashMap<>(versions);
  }
}
