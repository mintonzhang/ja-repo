package com.github.klboke.nexusplus.server.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NexusSecurityPayloads {
  private NexusSecurityPayloads() {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUser(
      String userId,
      String firstName,
      String lastName,
      String emailAddress,
      String source,
      String status,
      Boolean readOnly,
      Set<String> roles,
      Set<String> externalRoles) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusCreateUser(
      String userId,
      String firstName,
      String lastName,
      String emailAddress,
      String password,
      String status,
      Set<String> roles) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusRole(
      String id,
      String source,
      String name,
      String description,
      Set<String> privileges,
      Set<String> roles) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusPrivilege(
      String type,
      String name,
      String description,
      Boolean readOnly,
      String pattern,
      String domain,
      List<String> actions,
      String format,
      String repository,
      String contentSelector,
      Map<String, Object> properties,
      String permission) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusRealm(
      String id,
      String name) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusRealmSettings(
      List<String> realms) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUserSource(
      String id,
      String name) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusContentSelector(
      String name,
      String type,
      String description,
      String expression) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiSelector(
      String id,
      String name,
      String type,
      String description,
      String expression,
      List<String> usedBy,
      Integer usedByCount) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusAnonymousSettings(
      Boolean enabled,
      String userId,
      String realmName) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusLdapServer(
      String id,
      String name,
      Integer order,
      String url,
      String protocol,
      String host,
      Integer port,
      Boolean useTrustStore,
      String searchBase,
      String authScheme,
      String authRealm,
      String authUsername,
      String authPassword,
      Integer connectionTimeout,
      Integer connectionRetryDelay,
      Integer maxIncidentsCount,
      String userBaseDn,
      Boolean userSubtree,
      String userObjectClass,
      String userLdapFilter,
      String userIdAttribute,
      String userRealNameAttribute,
      String userMemberOfAttribute,
      String userEmailAddressAttribute,
      String userPasswordAttribute,
      Boolean ldapGroupsAsRoles,
      String groupType,
      String groupBaseDn,
      Boolean groupSubtree,
      String groupIdAttribute,
      String groupMemberAttribute,
      String groupMemberFormat,
      String groupObjectClass) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusLdapSchemaTemplate(
      String name,
      String userBaseDn,
      Boolean userSubtree,
      String userObjectClass,
      String userLdapFilter,
      String userIdAttribute,
      String userRealNameAttribute,
      String userEmailAddressAttribute,
      String userPasswordAttribute,
      Boolean ldapGroupsAsRoles,
      String userMemberOfAttribute,
      String groupType,
      String groupBaseDn,
      Boolean groupSubtree,
      String groupIdAttribute,
      String groupMemberAttribute,
      String groupMemberFormat,
      String groupObjectClass) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusLdapMappedUser(
      String userId,
      String name,
      String email,
      Set<String> roles) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusApiKey(
      Long id,
      String domain,
      String ownerSource,
      String ownerUserId,
      String displayName,
      String status,
      String tokenPrefix,
      Set<String> scopes,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      LocalDateTime expiresAt,
      LocalDateTime lastUsedAt) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusApiKeyCommand(
      String domain,
      String ownerSource,
      String ownerUserId,
      String displayName,
      String status,
      Set<String> scopes,
      LocalDateTime expiresAt,
      String encryptedPayload,
      String apiKeyHash) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusCreatedApiKey(
      NexusApiKey apiKey,
      String token) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiReference(
      String id,
      String name) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiRepositoryStatus(
      String repositoryName,
      Boolean online,
      String description,
      String reason) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiRepositoryReference(
      String id,
      String name,
      String type,
      String format,
      String versionPolicy,
      String url,
      NexusUiRepositoryStatus status,
      Integer sortOrder) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiPage<T>(
      int total,
      List<T> data) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiUser(
      String userId,
      String version,
      String realm,
      String firstName,
      String lastName,
      String email,
      String status,
      String password,
      Set<String> roles,
      Boolean external,
      Set<String> externalRoles) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiUserRoleMappings(
      String userId,
      String realm,
      Set<String> roles) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiRole(
      String id,
      String version,
      String source,
      String name,
      String description,
      Boolean readOnly,
      Set<String> privileges,
      Set<String> roles) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiPrivilege(
      String id,
      String version,
      String name,
      String description,
      String type,
      Boolean readOnly,
      Map<String, Object> properties,
      String permission) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiPermission(
      String id,
      Boolean permitted) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusCurrentUser(
      String id,
      Boolean authenticated,
      Boolean administrator,
      Set<String> authenticatedRealms) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUserAccount(
      String userId,
      String firstName,
      String lastName,
      String email,
      Boolean external) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUserAccountPassword(
      String authToken,
      String password) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusAuthToken(
      String u,
      String p) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusAuthTicket(
      String t) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiPrivilegeType(
      String id,
      String name,
      List<NexusUiFormField> formFields) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NexusUiFormField(
      String id,
      String type,
      String label,
      String helpText,
      Boolean required,
      Boolean disabled,
      Boolean readOnly,
      String regexValidation,
      String initialValue,
      Map<String, Object> attributes,
      String storeApi,
      Map<String, String> storeFilters,
      String idMapping,
      String nameMapping,
      Boolean allowAutocomplete) {
  }
}
