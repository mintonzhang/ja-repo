package com.github.klboke.nexusplus.server.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.klboke.nexusplus.persistence.mysql.dao.CacheVersionDao;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MysqlVersionWatermark implements VersionWatermark {
  private final CacheVersionDao dao;
  private final Cache<String, Long> localVersions;

  public MysqlVersionWatermark(
      CacheVersionDao dao,
      @Value("${nexus-plus.cache.version.local-ttl-seconds:2}") long localTtlSeconds) {
    this.dao = dao;
    this.localVersions = Caffeine.newBuilder()
        .expireAfterWrite(Math.max(1, localTtlSeconds), TimeUnit.SECONDS)
        .maximumSize(100_000)
        .build();
  }

  @Override
  public long bump(String name) {
    long version = dao.bump(name);
    localVersions.put(name, version);
    return version;
  }

  @Override
  public long current(String name) {
    return localVersions.get(name, dao::current);
  }

  @Override
  public Map<String, Long> currentAll() {
    Map<String, Long> versions = dao.selectAll();
    versions.forEach(localVersions::put);
    return versions;
  }
}
