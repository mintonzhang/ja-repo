package com.github.klboke.nexusplus.server.npm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NpmFormatAttributesTest {

  @Test
  void extractsNexusStyleAttributesFromPackageJson() {
    Map<String, Object> packageJson = new LinkedHashMap<>();
    packageJson.put("name", "@example/video-player");
    packageJson.put("version", "1.4.2");
    packageJson.put("description", "A modern video player");
    packageJson.put("license", "MIT");
    packageJson.put("keywords", List.of("video", "player", "hls"));
    packageJson.put("author", Map.of("name", "Example Team"));
    packageJson.put("repository", Map.of("type", "git", "url", "https://example.invalid/repo.git"));
    packageJson.put("bugs", Map.of("url", "https://example.invalid/issues"));
    packageJson.put("engines", Map.of("node", ">=18"));

    Map<String, Object> attributes = NpmFormatAttributes.extract(packageJson);

    assertEquals("example", attributes.get("scope"));
    assertEquals("MIT", attributes.get("license"));
    assertEquals("video player hls", attributes.get("keywords"));
    assertEquals("00001.00004.00002", attributes.get("search_normalized_version"));
    assertEquals("Example Team", attributes.get("author"));
    assertEquals("https://example.invalid/repo.git", attributes.get("repository_url"));
    assertEquals("git", attributes.get("repository_type"));
    assertEquals("https://example.invalid/issues", attributes.get("bugs_url"));
    assertEquals("@example/video-player", attributes.get("name"));
    assertEquals("A modern video player", attributes.get("description"));
    assertEquals("", attributes.get("tagged_is"));
    assertEquals("unstable", attributes.get("tagged_not"));
    assertEquals("1.4.2", attributes.get("version"));
    assertEquals(Map.of("node", ">=18"), attributes.get("engines"));
  }

  @Test
  void marksVersionsBeforeOneAsUnstable() {
    Map<String, Object> packageJson = Map.of(
        "name", "demo",
        "version", "0.9.0");

    Map<String, Object> attributes = NpmFormatAttributes.extract(packageJson);

    assertEquals("unstable", attributes.get("tagged_is"));
    assertEquals("", attributes.get("tagged_not"));
  }
}
