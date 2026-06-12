package com.github.klboke.nexusplus.server;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = "com.github.klboke.nexusplus",
    excludeName = {
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
    })
@EnableScheduling
public class NexusPlusApplication {
  private static final String APOLLO_CONFIG_IMPORT = "optional:apollo://";

  public static void main(String[] args) {
    configureApolloSystemProperties(System.getenv());
    SpringApplication.run(NexusPlusApplication.class, args);
  }

  static void configureApolloSystemProperties(Map<String, String> env) {
    String nexusApolloMeta = env.get("NEXUS_PLUS_APOLLO_META");
    if (!hasText(System.getProperty("apollo.meta")) && hasText(nexusApolloMeta)) {
      System.setProperty("apollo.meta", nexusApolloMeta.trim());
    }

    if (apolloMetaConfigured(env) && !springConfigImportConfigured(env)) {
      System.setProperty("spring.config.import", APOLLO_CONFIG_IMPORT);
    }
  }

  private static boolean apolloMetaConfigured(Map<String, String> env) {
    return hasText(System.getProperty("apollo.meta"))
        || hasText(env.get("APOLLO_META"))
        || hasText(env.get("NEXUS_PLUS_APOLLO_META"));
  }

  private static boolean springConfigImportConfigured(Map<String, String> env) {
    return hasText(System.getProperty("spring.config.import")) || hasText(env.get("SPRING_CONFIG_IMPORT"));
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
