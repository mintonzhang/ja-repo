package com.github.klboke.nexusplus.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InMemoryCacheConfiguration {
  @Bean
  @ConditionalOnMissingBean(SharedCache.class)
  @ConditionalOnProperty(
      prefix = "nexus-plus.cache",
      name = "backend",
      havingValue = "memory",
      matchIfMissing = true)
  SharedCache inMemorySharedCache(
      ObjectMapper objectMapper,
      @Value("${nexus-plus.cache.memory.maximum-size:500000}") long maximumSize,
      ObjectProvider<MeterRegistry> meterRegistry) {
    return new InMemorySharedCache(objectMapper, maximumSize, meterRegistry.getIfAvailable());
  }
}
