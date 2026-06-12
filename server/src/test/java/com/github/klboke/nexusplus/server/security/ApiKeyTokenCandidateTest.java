package com.github.klboke.nexusplus.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiKeyTokenCandidateTest {

  @Test
  void extractsNexusDomainPrefixAndRawToken() {
    List<ApiKeyTokenCandidate> candidates = ApiKeyTokenCandidate.fromPresentedToken("NpmToken.raw-value");

    assertEquals(2, candidates.size());
    assertEquals(new ApiKeyTokenCandidate("NpmToken", "raw-value"), candidates.get(0));
    assertEquals(new ApiKeyTokenCandidate(null, "NpmToken.raw-value"), candidates.get(1));
  }

  @Test
  void preservesExistingFullTokenHashFallback() {
    List<ApiKeyTokenCandidate> candidates = ApiKeyTokenCandidate.fromPresentedToken("nexus-plus-generated-token");

    assertEquals(2, candidates.size());
    assertEquals(new ApiKeyTokenCandidate("NpmToken", "nexus-plus-generated-token"), candidates.get(0));
    assertEquals(new ApiKeyTokenCandidate(null, "nexus-plus-generated-token"), candidates.get(1));
  }

  @Test
  void ignoresBlankTokens() {
    assertEquals(List.of(), ApiKeyTokenCandidate.fromPresentedToken("  "));
  }
}
