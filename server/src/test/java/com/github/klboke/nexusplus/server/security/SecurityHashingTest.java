package com.github.klboke.nexusplus.server.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecurityHashingTest {

  @Test
  void verifiesNexusShiro1PasswordHash() {
    String shiro1Hash = "$shiro1$SHA-512$1024$zjU1u+Zg9UNwuB+HEawvtA==$"
        + "IzF/OWzJxrqvB5FCe/2+UcZhhZYM2pTu0TEz7Ybnk65AbbEdUk9ntdtBzkN8P3gZby2qz6MHKqAe8Cjai9c4Gg==";

    assertTrue(SecurityHashing.verifyPassword(shiro1Hash, "admin123"));
    assertFalse(SecurityHashing.verifyPassword(shiro1Hash, "wrong-password"));
  }

  @Test
  void verifiesNexusLegacySha1AndMd5PasswordHashes() {
    assertTrue(SecurityHashing.verifyPassword("f865b53623b121fd34ee5426c792e5c33af8c227", "admin123"));
    assertTrue(SecurityHashing.verifyPassword("0192023a7bbd73250516f069df18b500", "admin123"));

    assertFalse(SecurityHashing.verifyPassword("f865b53623b121fd34ee5426c792e5c33af8c228", "admin123"));
    assertFalse(SecurityHashing.verifyPassword("0192023a7bbd73250516f069df18b501", "admin123"));
  }

  @Test
  void verifiesGeneratedPbkdf2PasswordHash() {
    String hash = SecurityHashing.hashPassword("local-password");

    assertTrue(SecurityHashing.verifyPassword(hash, "local-password"));
    assertFalse(SecurityHashing.verifyPassword(hash, "other-password"));
  }

  @Test
  void rejectsMalformedOrMissingPasswordHashes() {
    assertFalse(SecurityHashing.verifyPassword(null, "admin123"));
    assertFalse(SecurityHashing.verifyPassword("", "admin123"));
    assertFalse(SecurityHashing.verifyPassword("$shiro1$SHA-512$not-an-int$salt$hash", "admin123"));
    assertFalse(SecurityHashing.verifyPassword("not-a-known-hash", "admin123"));
  }
}
