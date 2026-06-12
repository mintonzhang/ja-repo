package com.github.klboke.nexusplus.server.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OutboundRequestPolicyTest {

  @Test
  void rejectsLoopbackByDefault() {
    OutboundRequestPolicy policy = new OutboundRequestPolicy(false, "");

    assertThrows(SecurityValidationException.class,
        () -> policy.validateHttpUri("http://127.0.0.1:8081/repository/maven-public/", "proxy.remoteUrl"));
  }

  @Test
  void allowedHostsCanOverrideResolvedAddressChecks() {
    OutboundRequestPolicy policy = new OutboundRequestPolicy(false, "localhost");

    assertDoesNotThrow(() ->
        policy.validateHttpUri("http://localhost:8081/repository/maven-public/", "proxy.remoteUrl"));
  }

  @Test
  void rejectsUnsupportedSchemes() {
    OutboundRequestPolicy policy = OutboundRequestPolicy.allowPrivateForTests();

    assertThrows(SecurityValidationException.class,
        () -> policy.validateHttpUri("file:///etc/passwd", "proxy.remoteUrl"));
  }
}
