package com.github.klboke.nexusplus.migration.nexus.security;

import java.util.List;
import java.util.Map;

public record NexusSecurityExport(
    List<Map<String, Object>> users,
    List<Map<String, Object>> roles,
    List<Map<String, Object>> privileges,
    List<Map<String, Object>> userRoleMappings,
    List<Map<String, Object>> apiKeys,
    List<Map<String, Object>> contentSelectors,
    List<Map<String, Object>> repositoryTargets,
    List<String> realmOrder,
    Map<String, Object> anonymous) {

  public NexusSecurityExport {
    users = safeDocuments(users);
    roles = safeDocuments(roles);
    privileges = safeDocuments(privileges);
    userRoleMappings = safeDocuments(userRoleMappings);
    apiKeys = safeDocuments(apiKeys);
    contentSelectors = safeDocuments(contentSelectors);
    repositoryTargets = safeDocuments(repositoryTargets);
    realmOrder = realmOrder == null ? List.of() : List.copyOf(realmOrder);
    anonymous = anonymous == null ? Map.of() : Map.copyOf(anonymous);
  }

  public static NexusSecurityExport empty() {
    return new NexusSecurityExport(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Map.of());
  }

  private static List<Map<String, Object>> safeDocuments(List<Map<String, Object>> documents) {
    return documents == null ? List.of() : documents.stream().map(Map::copyOf).toList();
  }
}
