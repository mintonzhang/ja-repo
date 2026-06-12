package com.github.klboke.nexusplus.server.security;

import com.github.klboke.nexusplus.core.security.EncryptionSecrets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Bridges the encryption secrets declared in the application config file
 * ({@code nexus-plus.security.encryption.*}) into the Spring-agnostic {@link EncryptionSecrets}
 * holder. Running as a constructor of an eagerly-created {@code @Configuration} bean, this completes
 * during context startup — before any request-time code resolves a secret — so config-file values
 * take precedence over the system-property / environment fallbacks.
 */
@Configuration
public class EncryptionSecretsConfigurer {
  public EncryptionSecretsConfigurer(
      @Value("${nexus-plus.security.encryption.credential-secret:}") String credentialSecret,
      @Value("${nexus-plus.security.encryption.api-key-payload-secret:}") String apiKeyPayloadSecret) {
    EncryptionSecrets.configure(credentialSecret, apiKeyPayloadSecret);
  }
}
