package com.github.klboke.nexusplus.server.security;

import com.github.klboke.nexusplus.server.security.SecurityPayloads.UserView;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NexusExternalRoles {
  private NexusExternalRoles() {
  }

  static Set<String> externalRoles(UserView user) {
    return user.external() ? externalRoles(user.attributes()) : Set.of();
  }

  static Set<String> visibleRoles(UserView user) {
    LinkedHashSet<String> roles = new LinkedHashSet<>();
    addStrings(roles, user.roles());
    addStrings(roles, externalRoles(user));
    return roles;
  }

  static List<String> localRoleList(UserView user, Set<String> submittedRoles) {
    LinkedHashSet<String> roles = new LinkedHashSet<>();
    addStrings(roles, submittedRoles);
    roles.removeAll(externalRoles(user));
    return List.copyOf(roles);
  }

  private static Set<String> externalRoles(Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return Set.of();
    }
    LinkedHashSet<String> roles = new LinkedHashSet<>();
    addStrings(roles, attributes.get("groups"));
    addStrings(roles, attributes.get("roles"));
    Object claims = attributes.get("claims");
    if (claims instanceof Map<?, ?> claimsMap) {
      addStrings(roles, claimsMap.get("groups"));
      addStrings(roles, claimsMap.get("roles"));
      Object realmAccess = claimsMap.get("realm_access");
      if (realmAccess instanceof Map<?, ?> realmAccessMap) {
        addStrings(roles, realmAccessMap.get("roles"));
      }
      Object resourceAccess = claimsMap.get("resource_access");
      if (resourceAccess instanceof Map<?, ?> resourceAccessMap) {
        for (Object value : resourceAccessMap.values()) {
          if (value instanceof Map<?, ?> resourceMap) {
            addStrings(roles, resourceMap.get("roles"));
          }
        }
      }
    }
    return roles;
  }

  private static void addStrings(Set<String> target, Object value) {
    if (value instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        addString(target, item);
      }
      return;
    }
    addString(target, value);
  }

  private static void addString(Set<String> target, Object value) {
    String text = value == null ? null : String.valueOf(value).trim();
    if (text != null && !text.isEmpty()) {
      target.add(text);
    }
  }
}
