package com.github.klboke.nexusplus.storage.file;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record FileBlobStoreConfig(
    long id,
    String name,
    String path,
    Path root) {
  public static final String TYPE = "file";
  public static final String ENGINE = "file";
  public static final String ATTR_PATH = "path";

  public FileBlobStoreConfig {
    path = path == null ? "" : path.trim();
    root = root == null ? Path.of(path).toAbsolutePath().normalize() : root.toAbsolutePath().normalize();
  }

  public static boolean isFileStore(String type, Map<String, Object> attributes) {
    if (TYPE.equals(normalize(type))) {
      return true;
    }
    Object engine = attributes == null ? null : attributes.get("engine");
    return ENGINE.equals(normalize(engine == null ? null : engine.toString()));
  }

  public String signature() {
    return ENGINE + "|" + root;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof FileBlobStoreConfig that)) return false;
    return id == that.id
        && Objects.equals(name, that.name)
        && Objects.equals(path, that.path)
        && Objects.equals(root, that.root);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, path, root);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
