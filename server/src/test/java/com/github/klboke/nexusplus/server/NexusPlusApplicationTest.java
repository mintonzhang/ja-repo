package com.github.klboke.nexusplus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NexusPlusApplicationTest {
  @Test
  void leavesApolloDisabledWhenMetaIsNotConfigured() {
    withApolloSystemPropertiesCleared(() -> {
      NexusPlusApplication.configureApolloSystemProperties(Map.of());

      assertNull(System.getProperty("apollo.meta"));
      assertNull(System.getProperty("spring.config.import"));
    });
  }

  @Test
  void mapsNexusApolloMetaToApolloSystemProperties() {
    withApolloSystemPropertiesCleared(() -> {
      NexusPlusApplication.configureApolloSystemProperties(Map.of(
          "NEXUS_PLUS_APOLLO_META", " http://apollo-config:8080 "));

      assertEquals("http://apollo-config:8080", System.getProperty("apollo.meta"));
      assertEquals("optional:apollo://", System.getProperty("spring.config.import"));
    });
  }

  @Test
  void preservesExplicitApolloAndImportSystemProperties() {
    withApolloSystemPropertiesCleared(() -> {
      System.setProperty("apollo.meta", "http://explicit-apollo:8080");
      System.setProperty("spring.config.import", "optional:file:/tmp/nexus-plus/");

      NexusPlusApplication.configureApolloSystemProperties(Map.of(
          "NEXUS_PLUS_APOLLO_META", "http://ignored-apollo:8080"));

      assertEquals("http://explicit-apollo:8080", System.getProperty("apollo.meta"));
      assertEquals("optional:file:/tmp/nexus-plus/", System.getProperty("spring.config.import"));
    });
  }

  private static void withApolloSystemPropertiesCleared(Runnable action) {
    String previousMeta = System.getProperty("apollo.meta");
    String previousImport = System.getProperty("spring.config.import");
    try {
      System.clearProperty("apollo.meta");
      System.clearProperty("spring.config.import");
      action.run();
    } finally {
      restoreSystemProperty("apollo.meta", previousMeta);
      restoreSystemProperty("spring.config.import", previousImport);
    }
  }

  private static void restoreSystemProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
