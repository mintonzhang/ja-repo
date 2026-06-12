package com.github.klboke.nexusplus.server.security;

import com.github.klboke.nexusplus.core.security.EncryptionSecrets;
import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails fast on startup when a reversible-encryption secret is left at its development default
 * outside the {@code dev}/{@code test} profiles. Both the credential secret (blob-store + realm
 * credentials) and the API-key payload secret must be supplied via the application config file
 * ({@code nexus-plus.security.encryption.*}), a system property, or an environment variable so that
 * production never silently encrypts with a well-known key.
 */
@Component
public class EncryptionSecretsVerifier implements ApplicationRunner {
  private final Environment environment;

  public EncryptionSecretsVerifier(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (developmentProfileActive()) {
      return;
    }
    if (!EncryptionSecrets.apiKeyPayloadSecretConfigured()) {
      throw new IllegalStateException(
          "nexus-plus.security.encryption.api-key-payload-secret (or NEXUS_PLUS_API_KEY_PAYLOAD_SECRET) "
              + "is required outside dev/test profiles");
    }
    if (!EncryptionSecrets.credentialSecretConfigured()) {
      throw new IllegalStateException(
          "nexus-plus.security.encryption.credential-secret (or NEXUS_PLUS_CREDENTIAL_SECRET) "
              + "is required outside dev/test profiles");
    }
  }

  private boolean developmentProfileActive() {
    return Arrays.stream(environment.getActiveProfiles())
        .anyMatch(profile -> profile.equalsIgnoreCase("dev") || profile.equalsIgnoreCase("test"));
  }
}
