package com.github.klboke.nexusplus.storage.file.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nexus-plus.storage.file")
public class FileStorageProperties {
  private String baseDir = "blobs";
  private boolean enabled = true;
  private boolean productionEnabled = false;
  private boolean sharedFilesystem = false;

  public String getBaseDir() {
    return baseDir;
  }

  public void setBaseDir(String baseDir) {
    this.baseDir = baseDir;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isProductionEnabled() {
    return productionEnabled;
  }

  public void setProductionEnabled(boolean productionEnabled) {
    this.productionEnabled = productionEnabled;
  }

  public boolean isSharedFilesystem() {
    return sharedFilesystem;
  }

  public void setSharedFilesystem(boolean sharedFilesystem) {
    this.sharedFilesystem = sharedFilesystem;
  }

  public Path baseDirPath() {
    String value = baseDir == null || baseDir.isBlank() ? "blobs" : baseDir.trim();
    return Path.of(value).toAbsolutePath().normalize();
  }

  public Path resolvePath(String path) {
    String value = path == null ? "" : path.trim();
    Path raw = Path.of(value);
    Path resolved = raw.isAbsolute() ? raw : baseDirPath().resolve(raw);
    return resolved.toAbsolutePath().normalize();
  }
}
