package com.github.klboke.nexusplus.server.npm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.artifact.versioning.ComparableVersion;

public final class NpmFormatAttributes {
  private static final String FIRST_STABLE_VERSION = "1.0.0";

  private NpmFormatAttributes() {
  }

  public static Map<String, Object> extract(Map<String, Object> packageJson) {
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    copy(out, "scope", scope(packageJson));
    copy(out, "license", license(packageJson.get("license")));
    copy(out, "keywords", stringCollection(packageJson.get("keywords")));
    copy(out, "search_normalized_version", normalizedVersion(packageJson.get("version")));
    copy(out, "author", person(packageJson.get("author")));
    copy(out, "contributors", contributors(packageJson.get("contributors")));
    copy(out, "os", stringCollection(packageJson.get("os")));
    copy(out, "cpu", stringCollection(packageJson.get("cpu")));
    copy(out, "engines", stringMap(packageJson.get("engines")));
    copy(out, "repository_url", stringFromMap(packageJson.get("repository"), "url"));
    copy(out, "repository_type", stringFromMap(packageJson.get("repository"), "type"));
    copy(out, "bugs_url", bugsUrl(packageJson.get("bugs")));
    copy(out, "bugs_email", stringFromMap(packageJson.get("bugs"), "email"));
    copy(out, "homepage", stringValue(packageJson.get("homepage")));
    copy(out, "name", packageJson.get("name"));
    copy(out, "description", packageJson.get("description"));
    boolean unstable = unstable(packageJson.get("version"));
    out.put("tagged_is", unstable ? "unstable" : "");
    out.put("tagged_not", unstable ? "" : "unstable");
    copy(out, "version", packageJson.get("version"));
    return out;
  }

  private static void copy(Map<String, Object> out, String key, Object value) {
    if (value != null) {
      out.put(key, value);
    }
  }

  private static String scope(Map<String, Object> packageJson) {
    Object name = packageJson.get("name");
    if (name instanceof String value && value.startsWith("@") && value.contains("/")) {
      return value.substring(1, value.indexOf('/'));
    }
    return null;
  }

  private static String person(Object value) {
    if (value instanceof Map<?, ?> map) {
      List<String> pieces = new ArrayList<>();
      Object name = map.get("name");
      Object email = map.get("email");
      Object url = map.get("url");
      if (name != null) pieces.add(name.toString());
      if (email != null) pieces.add("<" + email + ">");
      if (url != null) pieces.add("(" + url + ")");
      return String.join(" ", pieces);
    }
    return stringValue(value);
  }

  private static Object contributors(Object value) {
    if (value instanceof Collection<?> collection) {
      List<String> people = new ArrayList<>();
      for (Object item : collection) {
        String person = person(item);
        if (person != null && !person.isBlank()) {
          people.add(person);
        }
      }
      return people.isEmpty() ? null : people;
    }
    return person(value);
  }

  private static String stringCollection(Object value) {
    if (value instanceof Collection<?> collection) {
      List<String> strings = new ArrayList<>();
      for (Object item : collection) {
        if (item != null) {
          strings.add(item.toString());
        }
      }
      return String.join(" ", strings);
    }
    return stringValue(value);
  }

  private static Map<String, String> stringMap(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return null;
    }
    LinkedHashMap<String, String> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        result.put(entry.getKey().toString(), entry.getValue().toString());
      }
    }
    return result.isEmpty() ? null : result;
  }

  private static String license(Object value) {
    if (value instanceof Map<?, ?> map) {
      return stringValue(map.get("type"));
    }
    return stringValue(value);
  }

  private static String bugsUrl(Object value) {
    if (value instanceof String text) {
      return text;
    }
    return stringFromMap(value, "url");
  }

  private static String stringFromMap(Object value, String key) {
    if (value instanceof Map<?, ?> map) {
      return stringValue(map.get(key));
    }
    return null;
  }

  private static String stringValue(Object value) {
    return value == null ? null : value.toString();
  }

  private static String normalizedVersion(Object value) {
    String version = stringValue(value);
    if (version == null) {
      return null;
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < version.length();) {
      int codePoint = version.codePointAt(i);
      if (Character.isDigit(codePoint)) {
        StringBuilder digits = new StringBuilder();
        while (i < version.length()) {
          codePoint = version.codePointAt(i);
          if (!Character.isDigit(codePoint)) {
            break;
          }
          digits.appendCodePoint(codePoint);
          i += Character.charCount(codePoint);
        }
        out.append("0".repeat(Math.max(0, 5 - digits.length()))).append(digits);
      } else {
        out.appendCodePoint(codePoint);
        i += Character.charCount(codePoint);
      }
    }
    return out.toString();
  }

  private static boolean unstable(Object value) {
    String version = stringValue(value);
    if (version == null || version.isBlank()) {
      return true;
    }
    return new ComparableVersion(version).compareTo(new ComparableVersion(FIRST_STABLE_VERSION)) < 0;
  }
}
