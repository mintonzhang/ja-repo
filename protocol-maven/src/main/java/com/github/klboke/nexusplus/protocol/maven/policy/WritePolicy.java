package com.github.klboke.nexusplus.protocol.maven.policy;

public enum WritePolicy {
  ALLOW,
  ALLOW_ONCE,
  DENY;

  public static WritePolicy parse(String value) {
    if (value == null || value.isBlank()) return ALLOW_ONCE;
    return WritePolicy.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
  }

  public boolean checkCreateAllowed() {
    return this != DENY;
  }

  public boolean checkUpdateAllowed() {
    return this == ALLOW;
  }

  public boolean checkDeleteAllowed() {
    return this != DENY;
  }
}
