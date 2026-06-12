package com.github.klboke.nexusplus.server.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutboundRequestPolicy {
  private final boolean allowPrivateAddresses;
  private final Set<String> allowedHosts;

  @Autowired
  public OutboundRequestPolicy(
      @Value("${nexus-plus.security.outbound.allow-private-addresses:false}") boolean allowPrivateAddresses,
      @Value("${nexus-plus.security.outbound.allowed-hosts:}") String allowedHosts) {
    this.allowPrivateAddresses = allowPrivateAddresses;
    this.allowedHosts = parseHosts(allowedHosts);
  }

  private OutboundRequestPolicy(boolean allowPrivateAddresses, Set<String> allowedHosts) {
    this.allowPrivateAddresses = allowPrivateAddresses;
    this.allowedHosts = allowedHosts;
  }

  public static OutboundRequestPolicy allowPrivateForTests() {
    return new OutboundRequestPolicy(true, Set.of());
  }

  public URI validateHttpUri(String rawUrl, String purpose) {
    if (rawUrl == null || rawUrl.isBlank()) {
      throw new SecurityValidationException(purpose + " URL is required");
    }
    try {
      return validateHttpUri(new URI(rawUrl), purpose);
    } catch (URISyntaxException e) {
      throw new SecurityValidationException(purpose + " URL is not valid: " + e.getMessage(), e);
    }
  }

  public URI validateHttpUri(URI uri, String purpose) {
    if (uri == null) {
      throw new SecurityValidationException(purpose + " URL is required");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new SecurityValidationException(purpose + " URL must be http or https");
    }
    if (uri.getUserInfo() != null) {
      throw new SecurityValidationException(purpose + " URL must not include user info");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new SecurityValidationException(purpose + " URL must have a host");
    }
    validateHost(host, purpose);
    return uri;
  }

  private void validateHost(String host, String purpose) {
    String normalized = normalizeHost(host);
    if (allowedHosts.contains(normalized)) {
      return;
    }
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      throw new SecurityValidationException(purpose + " URL host cannot be resolved: " + host, e);
    }
    if (addresses.length == 0) {
      throw new SecurityValidationException(purpose + " URL host cannot be resolved: " + host);
    }
    if (!allowPrivateAddresses) {
      for (InetAddress address : addresses) {
        if (blockedAddress(address)) {
          throw new SecurityValidationException(
              purpose + " URL resolves to a private or local address: " + host + " -> " + address.getHostAddress());
        }
      }
    }
  }

  private boolean blockedAddress(InetAddress address) {
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || isIpv4MetadataAddress(address)
        || isIpv6UniqueLocal(address);
  }

  private static boolean isIpv4MetadataAddress(InetAddress address) {
    if (!(address instanceof Inet4Address)) {
      return false;
    }
    byte[] raw = address.getAddress();
    int a = raw[0] & 0xff;
    int b = raw[1] & 0xff;
    return (a == 169 && b == 254) || a == 0 || a == 127;
  }

  private static boolean isIpv6UniqueLocal(InetAddress address) {
    if (!(address instanceof Inet6Address)) {
      return false;
    }
    int first = address.getAddress()[0] & 0xff;
    return (first & 0xfe) == 0xfc;
  }

  private static Set<String> parseHosts(String value) {
    if (value == null || value.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(value.split(","))
        .map(OutboundRequestPolicy::normalizeHost)
        .filter(host -> !host.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static String normalizeHost(String host) {
    String value = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    if (value.startsWith("[") && value.endsWith("]")) {
      value = value.substring(1, value.length() - 1);
    }
    return value;
  }
}
