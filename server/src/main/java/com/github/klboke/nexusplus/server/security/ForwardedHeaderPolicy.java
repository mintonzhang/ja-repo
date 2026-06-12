package com.github.klboke.nexusplus.server.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ForwardedHeaderPolicy {
  private final Set<String> trustedProxies;

  public ForwardedHeaderPolicy(
      @Value("${nexus-plus.security.forwarded-headers.trusted-proxies:}") String trustedProxies) {
    this.trustedProxies = parse(trustedProxies);
  }

  public boolean trusted(HttpServletRequest request) {
    if (trustedProxies.isEmpty()) {
      return false;
    }
    String remote = request.getRemoteAddr();
    if (remote == null || remote.isBlank()) {
      return false;
    }
    return trustedProxies.contains(remote.trim()) || trustedProxies.contains(normalize(remote));
  }

  private static Set<String> parse(String value) {
    if (value == null || value.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .map(ForwardedHeaderPolicy::normalize)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static String normalize(String value) {
    try {
      return InetAddress.getByName(value).getHostAddress();
    } catch (Exception ignored) {
      return value;
    }
  }
}
