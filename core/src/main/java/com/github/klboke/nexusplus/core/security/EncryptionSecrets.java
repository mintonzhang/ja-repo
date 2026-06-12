package com.github.klboke.nexusplus.core.security;

/**
 * Central resolver for the secrets used by the application's reversible encryption.
 *
 * <ul>
 *   <li><b>credential secret</b> — encrypts server-held third-party credentials at rest
 *       ({@link SecretCipher}): blob-store S3 keys and authentication-realm secrets such as OIDC
 *       {@code clientSecret} and LDAP bind passwords.</li>
 *   <li><b>API-key payload secret</b> — encrypts user-facing API-key tokens
 *       ({@link ApiKeyTokenPayloads}); kept separate because it guards already-persisted data.</li>
 * </ul>
 *
 * <p>Secrets are configuration, so the primary source is the application config file: the Spring
 * context calls {@link #configure(String, String)} during startup with the bound
 * {@code nexus-plus.security.encryption.*} values. For contexts without Spring (e.g. the migration
 * CLI) resolution falls back to a JVM system property, then an environment variable, then a
 * development-only default — so every secret keeps a single, well-defined lookup order regardless
 * of how the process is launched.
 */
public final class EncryptionSecrets {
  static final String DEV_CREDENTIAL_SECRET = "nexus-plus-development-credential-secret";
  static final String DEV_API_KEY_PAYLOAD_SECRET = "nexus-plus-development-api-key-payload-secret";

  private static volatile String credentialSecretOverride;
  private static volatile String apiKeyPayloadSecretOverride;

  private EncryptionSecrets() {
  }

  /**
   * Installs the secrets bound from the application config file. Blank values are ignored so that
   * an unset config key transparently falls back to the system property / environment / default
   * chain. Safe to call more than once (e.g. on context refresh).
   */
  public static void configure(String credentialSecret, String apiKeyPayloadSecret) {
    if (credentialSecret != null && !credentialSecret.isBlank()) {
      credentialSecretOverride = credentialSecret;
    }
    if (apiKeyPayloadSecret != null && !apiKeyPayloadSecret.isBlank()) {
      apiKeyPayloadSecretOverride = apiKeyPayloadSecret;
    }
  }

  public static String credentialSecret() {
    return resolve(
        credentialSecretOverride,
        "nexus-plus.security.encryption.credential-secret",
        "NEXUS_PLUS_CREDENTIAL_SECRET",
        DEV_CREDENTIAL_SECRET);
  }

  public static String apiKeyPayloadSecret() {
    return resolve(
        apiKeyPayloadSecretOverride,
        "nexus-plus.security.api-key-payload-secret",
        "NEXUS_PLUS_API_KEY_PAYLOAD_SECRET",
        DEV_API_KEY_PAYLOAD_SECRET);
  }

  /** Whether an explicit credential secret is configured (config file, system property, or env). */
  public static boolean credentialSecretConfigured() {
    return configured(
        credentialSecretOverride,
        "nexus-plus.security.encryption.credential-secret",
        "NEXUS_PLUS_CREDENTIAL_SECRET");
  }

  /** Whether an explicit API-key payload secret is configured (config file, system property, or env). */
  public static boolean apiKeyPayloadSecretConfigured() {
    return configured(
        apiKeyPayloadSecretOverride,
        "nexus-plus.security.api-key-payload-secret",
        "NEXUS_PLUS_API_KEY_PAYLOAD_SECRET");
  }

  private static boolean configured(String override, String systemProperty, String environmentVariable) {
    return nonBlank(override)
        || nonBlank(System.getProperty(systemProperty))
        || nonBlank(System.getenv(environmentVariable));
  }

  private static boolean nonBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static String resolve(
      String override, String systemProperty, String environmentVariable, String developmentFallback) {
    if (override != null && !override.isBlank()) {
      return override;
    }
    String property = System.getProperty(systemProperty);
    if (property != null && !property.isBlank()) {
      return property;
    }
    String environment = System.getenv(environmentVariable);
    if (environment != null && !environment.isBlank()) {
      return environment;
    }
    return developmentFallback;
  }
}
