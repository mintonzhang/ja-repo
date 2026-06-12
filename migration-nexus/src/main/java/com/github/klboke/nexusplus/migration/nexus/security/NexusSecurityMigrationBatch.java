package com.github.klboke.nexusplus.migration.nexus.security;

import com.github.klboke.nexusplus.migration.nexus.security.NexusSecurityRecordMapper.NexusApiKey;
import com.github.klboke.nexusplus.migration.nexus.security.NexusSecurityRecordMapper.NexusAnonymousConfig;
import com.github.klboke.nexusplus.migration.nexus.security.NexusSecurityRecordMapper.NexusContentSelector;
import com.github.klboke.nexusplus.migration.nexus.security.NexusSecurityRecordMapper.NexusPrivilege;
import com.github.klboke.nexusplus.migration.nexus.security.NexusSecurityRecordMapper.NexusRole;
import com.github.klboke.nexusplus.migration.nexus.security.NexusSecurityRecordMapper.NexusUser;
import com.github.klboke.nexusplus.migration.nexus.security.NexusSecurityRecordMapper.NexusUserRoleMapping;
import java.util.List;

public record NexusSecurityMigrationBatch(
    List<NexusContentSelector> contentSelectors,
    List<NexusPrivilege> privileges,
    List<NexusRole> roles,
    List<NexusUser> users,
    List<NexusUserRoleMapping> userRoleMappings,
    List<String> realmOrder,
    List<NexusApiKey> apiKeys,
    NexusAnonymousConfig anonymousConfig) {

  public NexusSecurityMigrationBatch {
    contentSelectors = safe(contentSelectors);
    privileges = safe(privileges);
    roles = safe(roles);
    users = safe(users);
    userRoleMappings = safe(userRoleMappings);
    realmOrder = safe(realmOrder);
    apiKeys = safe(apiKeys);
  }

  public static NexusSecurityMigrationBatch empty() {
    return new NexusSecurityMigrationBatch(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);
  }

  private static <T> List<T> safe(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
