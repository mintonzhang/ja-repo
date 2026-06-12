package com.github.klboke.nexusplus.migration.nexus.security;

import com.github.klboke.nexusplus.persistence.mysql.model.ApiKeyRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.SecurityAnonymousConfigRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.SecurityPrivilegeRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.SecurityRealmRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.SecurityRepositoryTargetRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.SecurityRoleRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.SecurityUserRecord;
import java.util.List;

public interface NexusSecurityMigrationWriter {
  void upsertRepositoryTarget(SecurityRepositoryTargetRecord record);

  void upsertPrivilege(SecurityPrivilegeRecord record);

  void upsertRole(SecurityRoleRecord record);

  void replaceRolePrivileges(String roleId, List<String> privilegeIds);

  void replaceRoleInheritance(String roleId, List<String> childRoleIds);

  void upsertUser(SecurityUserRecord record);

  void replaceUserRoles(String source, String userId, List<String> roleIds);

  void upsertRealm(SecurityRealmRecord record);

  void updateRealmConfig(List<String> activeRealmIds);

  void upsertAnonymousConfig(SecurityAnonymousConfigRecord record);

  void upsertApiKey(ApiKeyRecord record);
}
