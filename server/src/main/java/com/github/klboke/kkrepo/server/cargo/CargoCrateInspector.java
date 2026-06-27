package com.github.klboke.kkrepo.server.cargo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

final class CargoCrateInspector {
  private CargoCrateInspector() {
  }

  static Manifest inspect(Path crateFile) {
    try (var file = Files.newInputStream(crateFile);
        var gzip = new GzipCompressorInputStream(file);
        var tar = new TarArchiveInputStream(gzip)) {
      TarArchiveEntry entry;
      while ((entry = tar.getNextEntry()) != null) {
        if (!entry.isFile() || !entry.getName().endsWith("/Cargo.toml")) {
          continue;
        }
        return parseManifest(tar);
      }
    } catch (IOException e) {
      throw new CargoExceptions.BadRequestException("Invalid .crate archive", e);
    }
    throw new CargoExceptions.BadRequestException(".crate archive does not contain Cargo.toml");
  }

  private static Manifest parseManifest(TarArchiveInputStream in) throws IOException {
    String section = "";
    Map<String, Object> packageValues = new LinkedHashMap<>();
    Map<String, Object> features = new LinkedHashMap<>();
    List<Object> dependencies = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      for (String trimmed : logicalLines(reader)) {
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
          section = trimmed.substring(1, trimmed.length() - 1).trim();
          continue;
        }
        int equals = topLevelEquals(trimmed);
        if (equals < 0) {
          continue;
        }
        String key = normalizeKey(trimmed.substring(0, equals));
        Object value = parseValue(trimmed.substring(equals + 1).trim());
        if ("package".equals(section)) {
          if ("name".equals(key)) {
            packageValues.put("name", text(value));
          } else if ("version".equals(key)) {
            packageValues.put("version", text(value));
          } else {
            packageValues.put(key, value);
          }
        } else if ("features".equals(section)) {
          features.put(key, stringList(value));
        } else {
          DependencySection dependencySection = dependencySection(section);
          if (dependencySection != null) {
            Map<String, Object> dependency = dependency(key, value, dependencySection);
            if (dependency != null) {
              dependencies.add(dependency);
            }
          }
        }
      }
    }
    String name = text(packageValues.get("name"));
    String version = text(packageValues.get("version"));
    if (name == null || version == null) {
      throw new CargoExceptions.BadRequestException("Cargo.toml is missing package name or version");
    }
    Map<String, Object> publishJson = new LinkedHashMap<>();
    publishJson.put("name", name);
    publishJson.put("vers", version);
    publishJson.put("deps", dependencies);
    publishJson.put("features", features);
    putText(publishJson, "description", packageValues.get("description"));
    putText(publishJson, "documentation", packageValues.get("documentation"));
    putText(publishJson, "homepage", packageValues.get("homepage"));
    putText(publishJson, "license", packageValues.get("license"));
    putText(publishJson, "license_file", packageValues.get("license-file"));
    putText(publishJson, "repository", packageValues.get("repository"));
    putText(publishJson, "links", packageValues.get("links"));
    putText(publishJson, "rust_version", packageValues.get("rust-version"));
    Object keywords = packageValues.get("keywords");
    publishJson.put("keywords", keywords == null ? List.of() : stringList(keywords));
    Object categories = packageValues.get("categories");
    publishJson.put("categories", categories == null ? List.of() : stringList(categories));
    Object authors = packageValues.get("authors");
    publishJson.put("authors", authors == null ? List.of() : stringList(authors));
    Object readme = packageValues.get("readme");
    if (readme instanceof String) {
      publishJson.put("readme_file", readme);
    }
    return new Manifest(name, version, publishJson);
  }

  private static List<String> logicalLines(BufferedReader reader) throws IOException {
    List<String> lines = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      String trimmed = stripComment(line).trim();
      if (trimmed.isBlank()) {
        continue;
      }
      if (!current.isEmpty()) {
        current.append(' ');
      }
      current.append(trimmed);
      if (balanced(current)) {
        lines.add(current.toString());
        current.setLength(0);
      }
    }
    if (!current.isEmpty()) {
      lines.add(current.toString());
    }
    return lines;
  }

  private static boolean balanced(CharSequence value) {
    boolean inDouble = false;
    boolean inSingle = false;
    boolean escaped = false;
    int square = 0;
    int brace = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (inDouble && c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '"' && !inSingle) {
        inDouble = !inDouble;
        continue;
      }
      if (c == '\'' && !inDouble) {
        inSingle = !inSingle;
        continue;
      }
      if (inDouble || inSingle) {
        continue;
      }
      if (c == '[') square++;
      if (c == ']') square--;
      if (c == '{') brace++;
      if (c == '}') brace--;
    }
    return square <= 0 && brace <= 0 && !inDouble && !inSingle;
  }

  private static String stripComment(String line) {
    boolean inString = false;
    boolean inLiteralString = false;
    boolean escaped = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '"' && !inLiteralString) {
        inString = !inString;
        continue;
      }
      if (c == '\'' && !inString) {
        inLiteralString = !inLiteralString;
        continue;
      }
      if (c == '#' && !inString && !inLiteralString) {
        return line.substring(0, i);
      }
    }
    return line;
  }

  private static int topLevelEquals(String value) {
    boolean inDouble = false;
    boolean inSingle = false;
    boolean escaped = false;
    int square = 0;
    int brace = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (inDouble && c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '"' && !inSingle) {
        inDouble = !inDouble;
        continue;
      }
      if (c == '\'' && !inDouble) {
        inSingle = !inSingle;
        continue;
      }
      if (inDouble || inSingle) {
        continue;
      }
      if (c == '[') square++;
      if (c == ']') square--;
      if (c == '{') brace++;
      if (c == '}') brace--;
      if (c == '=' && square == 0 && brace == 0) {
        return i;
      }
    }
    return -1;
  }

  private static Object parseValue(String value) {
    String trimmed = value.trim();
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      return parseArray(trimmed.substring(1, trimmed.length() - 1));
    }
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      return parseInlineTable(trimmed.substring(1, trimmed.length() - 1));
    }
    if ("true".equalsIgnoreCase(trimmed)) {
      return true;
    }
    if ("false".equalsIgnoreCase(trimmed)) {
      return false;
    }
    return unquote(trimmed);
  }

  private static List<Object> parseArray(String value) {
    List<Object> result = new ArrayList<>();
    for (String item : splitTopLevel(value, ',')) {
      String trimmed = item.trim();
      if (!trimmed.isBlank()) {
        result.add(parseValue(trimmed));
      }
    }
    return result;
  }

  private static Map<String, Object> parseInlineTable(String value) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String item : splitTopLevel(value, ',')) {
      int equals = topLevelEquals(item);
      if (equals < 0) {
        continue;
      }
      result.put(normalizeKey(item.substring(0, equals)), parseValue(item.substring(equals + 1)));
    }
    return result;
  }

  private static List<String> splitTopLevel(String value, char separator) {
    List<String> parts = new ArrayList<>();
    boolean inDouble = false;
    boolean inSingle = false;
    boolean escaped = false;
    int square = 0;
    int brace = 0;
    int start = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (inDouble && c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '"' && !inSingle) {
        inDouble = !inDouble;
        continue;
      }
      if (c == '\'' && !inDouble) {
        inSingle = !inSingle;
        continue;
      }
      if (inDouble || inSingle) {
        continue;
      }
      if (c == '[') square++;
      if (c == ']') square--;
      if (c == '{') brace++;
      if (c == '}') brace--;
      if (c == separator && square == 0 && brace == 0) {
        parts.add(value.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(value.substring(start));
    return parts;
  }

  private static Map<String, Object> dependency(
      String key,
      Object value,
      DependencySection section) {
    String manifestName = normalizeKey(key);
    String packageName = manifestName;
    String versionReq = null;
    List<String> features = List.of();
    boolean optional = false;
    boolean defaultFeatures = true;
    String registry = null;
    if (value instanceof String s) {
      versionReq = s;
    } else if (value instanceof Map<?, ?> table) {
      versionReq = text(table.get("version"));
      String packageValue = text(table.get("package"));
      if (packageValue != null) {
        packageName = packageValue;
      }
      features = stringList(table.get("features"));
      optional = booleanValue(table.get("optional"), false);
      Object defaultValue = table.containsKey("default-features")
          ? table.get("default-features")
          : table.get("default_features");
      defaultFeatures = booleanValue(defaultValue, true);
      registry = text(table.get("registry"));
    }
    if (versionReq == null) {
      return null;
    }
    Map<String, Object> dependency = new LinkedHashMap<>();
    dependency.put("name", packageName);
    dependency.put("version_req", versionReq);
    dependency.put("features", features);
    dependency.put("optional", optional);
    dependency.put("default_features", defaultFeatures);
    dependency.put("target", section.target());
    dependency.put("kind", section.kind());
    dependency.put("registry", registry);
    dependency.put("explicit_name_in_toml", packageName.equals(manifestName) ? null : manifestName);
    return dependency;
  }

  private static DependencySection dependencySection(String section) {
    if ("dependencies".equals(section)) {
      return new DependencySection("normal", null);
    }
    if ("dev-dependencies".equals(section)) {
      return new DependencySection("dev", null);
    }
    if ("build-dependencies".equals(section)) {
      return new DependencySection("build", null);
    }
    if (!section.startsWith("target.")) {
      return null;
    }
    String suffix = null;
    String kind = null;
    if (section.endsWith(".dev-dependencies")) {
      suffix = ".dev-dependencies";
      kind = "dev";
    } else if (section.endsWith(".build-dependencies")) {
      suffix = ".build-dependencies";
      kind = "build";
    } else if (section.endsWith(".dependencies")) {
      suffix = ".dependencies";
      kind = "normal";
    }
    if (suffix == null) {
      return null;
    }
    String target = section.substring("target.".length(), section.length() - suffix.length());
    return new DependencySection(kind, unquote(target));
  }

  private static List<String> stringList(Object value) {
    if (!(value instanceof Iterable<?> iterable)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object item : iterable) {
      String text = text(item);
      if (text != null) {
        result.add(text);
      }
    }
    return result;
  }

  private static boolean booleanValue(Object value, boolean fallback) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value == null) {
      return fallback;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }

  private static void putText(Map<String, Object> target, String key, Object value) {
    String text = text(value);
    if (text != null) {
      target.put(key, text);
    }
  }

  private static String text(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isBlank() ? null : text;
  }

  private static String normalizeKey(String key) {
    return unquote(key.trim());
  }

  private static String unquote(String value) {
    String trimmed = value.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      String body = trimmed.substring(1, trimmed.length() - 1);
      StringBuilder out = new StringBuilder(body.length());
      boolean escaped = false;
      for (int i = 0; i < body.length(); i++) {
        char c = body.charAt(i);
        if (!escaped) {
          if (c == '\\') {
            escaped = true;
          } else {
            out.append(c);
          }
          continue;
        }
        out.append(switch (c) {
          case 'n' -> '\n';
          case 'r' -> '\r';
          case 't' -> '\t';
          default -> c;
        });
        escaped = false;
      }
      if (escaped) {
        out.append('\\');
      }
      return out.toString();
    }
    if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }

  private record DependencySection(String kind, String target) {
  }

  record Manifest(String name, String version, Map<String, Object> publishJson) {
  }
}
