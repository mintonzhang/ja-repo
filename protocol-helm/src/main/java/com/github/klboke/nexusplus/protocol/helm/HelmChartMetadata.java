package com.github.klboke.nexusplus.protocol.helm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record HelmChartMetadata(
    String apiVersion,
    String name,
    String version,
    String description,
    String appVersion,
    String icon,
    List<String> sources,
    List<Map<String, String>> maintainers,
    Map<String, Object> raw) {

  public static HelmChartMetadata fromYamlMap(Map<String, Object> yaml) {
    Map<String, Object> raw = yaml == null ? Map.of() : new LinkedHashMap<>(yaml);
    return new HelmChartMetadata(
        string(raw.get("apiVersion")),
        string(raw.get("name")),
        string(raw.get("version")),
        string(raw.get("description")),
        string(raw.get("appVersion")),
        string(raw.get("icon")),
        stringList(raw.get("sources")),
        maintainerList(raw.get("maintainers")),
        raw);
  }

  public void requireNameAndVersion() {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Helm chart metadata is missing name");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("Helm chart metadata is missing version");
    }
  }

  public Map<String, Object> attributes() {
    Map<String, Object> attributes = new LinkedHashMap<>();
    put(attributes, "apiVersion", apiVersion);
    put(attributes, "name", name);
    put(attributes, "version", version);
    put(attributes, "description", description);
    put(attributes, "appVersion", appVersion);
    put(attributes, "icon", icon);
    if (sources != null && !sources.isEmpty()) attributes.put("sources", sources);
    if (maintainers != null && !maintainers.isEmpty()) attributes.put("maintainers", maintainers);
    return attributes;
  }

  private static void put(Map<String, Object> map, String key, String value) {
    if (value != null && !value.isBlank()) map.put(key, value);
  }

  private static String string(Object value) {
    return value == null ? null : value.toString();
  }

  private static List<String> stringList(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    return list.stream()
        .filter(item -> item != null && !item.toString().isBlank())
        .map(Object::toString)
        .toList();
  }

  private static List<Map<String, String>> maintainerList(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    return list.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(HelmChartMetadata::stringMap)
        .filter(map -> !map.isEmpty())
        .toList();
  }

  private static Map<String, String> stringMap(Map<?, ?> raw) {
    Map<String, String> result = new LinkedHashMap<>();
    raw.forEach((key, value) -> {
      if (key != null && value != null) result.put(key.toString(), value.toString());
    });
    return result;
  }
}
