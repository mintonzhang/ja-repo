package com.github.klboke.nexusplus.auth;

public record AccessDecision(
    boolean allowed,
    String reason) {
  public static AccessDecision allow() {
    return new AccessDecision(true, "allowed");
  }

  public static AccessDecision deny(String reason) {
    return new AccessDecision(false, reason);
  }
}
