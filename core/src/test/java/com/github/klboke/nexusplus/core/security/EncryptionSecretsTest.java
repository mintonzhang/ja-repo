package com.github.klboke.nexusplus.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

// configure() mutates process-wide static state, so the unconfigured case must run first.
@TestMethodOrder(OrderAnnotation.class)
class EncryptionSecretsTest {
  @Test
  @Order(1)
  void fallsBackToDevelopmentDefaultsWhenUnconfigured() {
    assertFalse(EncryptionSecrets.credentialSecretConfigured());
    assertFalse(EncryptionSecrets.apiKeyPayloadSecretConfigured());
    assertEquals(EncryptionSecrets.DEV_CREDENTIAL_SECRET, EncryptionSecrets.credentialSecret());
    assertEquals(EncryptionSecrets.DEV_API_KEY_PAYLOAD_SECRET, EncryptionSecrets.apiKeyPayloadSecret());
  }

  @Test
  @Order(2)
  void blankConfiguredValuesAreIgnored() {
    EncryptionSecrets.configure("  ", "");

    assertFalse(EncryptionSecrets.credentialSecretConfigured());
    assertEquals(EncryptionSecrets.DEV_CREDENTIAL_SECRET, EncryptionSecrets.credentialSecret());
  }

  @Test
  @Order(3)
  void configuredValuesTakePrecedence() {
    EncryptionSecrets.configure("credential-secret-from-config", "api-secret-from-config");

    assertTrue(EncryptionSecrets.credentialSecretConfigured());
    assertTrue(EncryptionSecrets.apiKeyPayloadSecretConfigured());
    assertEquals("credential-secret-from-config", EncryptionSecrets.credentialSecret());
    assertEquals("api-secret-from-config", EncryptionSecrets.apiKeyPayloadSecret());
  }
}
