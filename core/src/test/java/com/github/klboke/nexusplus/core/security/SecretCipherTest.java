package com.github.klboke.nexusplus.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecretCipherTest {
  private final SecretCipher cipher = new SecretCipher("unit-test-secret");

  @Test
  void encryptThenDecryptRoundTripsPlaintext() {
    String plaintext = "AKIAIOSFODNN7EXAMPLE/wJalrXUtnFEMI";

    String encrypted = cipher.encrypt(plaintext);

    assertTrue(SecretCipher.isEncrypted(encrypted), "encrypt must produce a tagged ciphertext");
    assertFalse(encrypted.contains(plaintext), "ciphertext must not embed the plaintext");
    assertEquals(plaintext, cipher.decrypt(encrypted));
  }

  @Test
  void encryptIsNonDeterministicButBothDecrypt() {
    String plaintext = "same-secret";

    String first = cipher.encrypt(plaintext);
    String second = cipher.encrypt(plaintext);

    assertFalse(first.equals(second), "a random IV must make ciphertexts differ");
    assertEquals(plaintext, cipher.decrypt(first));
    assertEquals(plaintext, cipher.decrypt(second));
  }

  @Test
  void encryptIsIdempotentOnAlreadyEncryptedValues() {
    String once = cipher.encrypt("token");

    assertEquals(once, cipher.encrypt(once), "already-encrypted values must not be double-encrypted");
  }

  @Test
  void decryptReturnsLegacyPlaintextUnchanged() {
    assertEquals("legacy-plaintext", cipher.decrypt("legacy-plaintext"));
  }

  @Test
  void nullAndEmptyValuesPassThrough() {
    assertNull(cipher.encrypt(null));
    assertNull(cipher.decrypt(null));
    assertEquals("", cipher.encrypt(""));
  }

  @Test
  void decryptWithWrongKeyFails() {
    String encrypted = cipher.encrypt("top-secret");
    SecretCipher otherKey = new SecretCipher("a-different-secret");

    assertThrows(IllegalStateException.class, () -> otherKey.decrypt(encrypted));
  }
}
