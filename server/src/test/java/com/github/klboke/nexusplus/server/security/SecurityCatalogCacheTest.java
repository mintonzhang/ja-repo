package com.github.klboke.nexusplus.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.klboke.nexusplus.auth.PermissionSubject;
import com.github.klboke.nexusplus.persistence.mysql.dao.SecurityDao;
import com.github.klboke.nexusplus.persistence.mysql.model.SecurityPrivilegeRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.SecurityRepositoryTargetRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.SecurityRoleRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.SecurityUserRecord;
import com.github.klboke.nexusplus.server.catalog.CatalogCacheBroadcaster;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecurityCatalogCacheTest {

  @Test
  void serviceUsesInMemoryCatalogForPermissionChecks() {
    FakeSecurityDao dao = new FakeSecurityDao();
    dao.user(1L, "Local", "alice", List.of("nx-parent"));
    dao.role("nx-parent");
    dao.role("nx-child");
    dao.inherit("nx-parent", "nx-child");
    dao.grant("nx-child", privilege("nx-users-read", "wildcard", Map.of("pattern", "nexus:users:read")));
    SecurityCatalogCache catalogCache = new SecurityCatalogCache(dao, true);
    SecurityManagementService service = new SecurityManagementService(dao, null, null, catalogCache);

    assertTrue(service.decide(subject("alice"), "nexus:users:read").allowed());
    assertTrue(service.listEffectivePermissions(subject("alice")).contains("nexus:users:read"));
    assertEquals(0, dao.listPrivilegesForRolesCalls);
  }

  @Test
  void scheduledSyncRefreshesDatabaseChangesIntoMemory() {
    FakeSecurityDao dao = new FakeSecurityDao();
    dao.user(1L, "Local", "alice", List.of("nx-reader"));
    dao.role("nx-reader");
    dao.grant("nx-reader", privilege("nx-users-read", "wildcard", Map.of("pattern", "nexus:users:read")));
    SecurityCatalogCache catalogCache = new SecurityCatalogCache(dao, true);
    SecurityManagementService service = new SecurityManagementService(dao, null, null, catalogCache);

    assertTrue(service.decide(subject("alice"), "nexus:users:read").allowed());
    dao.grant("nx-reader", privilege("nx-roles-read", "wildcard", Map.of("pattern", "nexus:roles:read")));

    assertFalse(service.decide(subject("alice"), "nexus:roles:read").allowed());

    catalogCache.syncDatabaseToMemory();

    assertTrue(service.decide(subject("alice"), "nexus:roles:read").allowed());
  }

  @Test
  void mutationBroadcastRefreshesSiblingCatalogImmediately() {
    FakeSecurityDao dao = new FakeSecurityDao();
    dao.user(1L, "Local", "alice", List.of("nx-reader"));
    dao.role("nx-reader");
    dao.grant("nx-reader", privilege("nx-users-read", "wildcard", Map.of("pattern", "nexus:users:read")));
    InMemoryBroadcaster broadcaster = new InMemoryBroadcaster();
    SecurityCatalogCache writerCatalog = new SecurityCatalogCache(dao, true, broadcaster);
    SecurityCatalogCache siblingCatalog = new SecurityCatalogCache(dao, true, broadcaster);
    SecurityManagementService siblingService = new SecurityManagementService(dao, null, null, siblingCatalog);

    writerCatalog.warmUp();
    siblingCatalog.warmUp();
    assertTrue(siblingService.decide(subject("alice"), "nexus:users:read").allowed());

    dao.grant("nx-reader", privilege("nx-roles-read", "wildcard", Map.of("pattern", "nexus:roles:read")));
    assertFalse(siblingService.decide(subject("alice"), "nexus:roles:read").allowed());

    writerCatalog.refreshAfterCommit();

    assertTrue(siblingService.decide(subject("alice"), "nexus:roles:read").allowed());
    assertEquals(1, broadcaster.publishCalls);
  }

  private static PermissionSubject subject(String userId) {
    return new PermissionSubject("Local", userId, Set.of(), null);
  }

  private static SecurityPrivilegeRecord privilege(
      String privilegeId,
      String type,
      Map<String, Object> properties) {
    return new SecurityPrivilegeRecord(privilegeId, privilegeId, null, type, false, properties);
  }

  private static class FakeSecurityDao extends SecurityDao {
    private final List<SecurityUserRecord> users = new ArrayList<>();
    private final Map<Long, List<String>> userRoles = new LinkedHashMap<>();
    private final Map<String, SecurityRoleRecord> roles = new LinkedHashMap<>();
    private final Map<String, List<String>> childRoles = new LinkedHashMap<>();
    private final Map<String, List<String>> rolePrivileges = new LinkedHashMap<>();
    private final Map<String, SecurityPrivilegeRecord> privileges = new LinkedHashMap<>();
    int listPrivilegesForRolesCalls;

    private FakeSecurityDao() {
      super(null, null);
    }

    private void user(long id, String source, String userId, List<String> roleIds) {
      users.add(new SecurityUserRecord(
          id,
          source,
          userId,
          userId,
          null,
          userId + "@example.invalid",
          "hash",
          "ACTIVE",
          null,
          Map.of()));
      userRoles.put(id, new ArrayList<>(roleIds));
    }

    private void role(String roleId) {
      roles.put(roleId, new SecurityRoleRecord(roleId, "Local", roleId, null, false, Map.of()));
    }

    private void inherit(String roleId, String childRoleId) {
      childRoles.computeIfAbsent(roleId, ignored -> new ArrayList<>()).add(childRoleId);
    }

    private void grant(String roleId, SecurityPrivilegeRecord privilege) {
      privileges.put(privilege.privilegeId(), privilege);
      rolePrivileges.computeIfAbsent(roleId, ignored -> new ArrayList<>()).add(privilege.privilegeId());
    }

    @Override
    public List<SecurityUserRecord> listUsers() {
      return List.copyOf(users);
    }

    @Override
    public List<String> listUserRoleIds(long userNumericId) {
      return userRoles.getOrDefault(userNumericId, List.of());
    }

    @Override
    public List<SecurityRoleRecord> listRoles() {
      return new ArrayList<>(roles.values());
    }

    @Override
    public List<String> listRoleChildIds(String roleId) {
      return childRoles.getOrDefault(roleId, List.of());
    }

    @Override
    public List<String> listRolePrivilegeIds(String roleId) {
      return rolePrivileges.getOrDefault(roleId, List.of());
    }

    @Override
    public List<SecurityPrivilegeRecord> listPrivileges() {
      return new ArrayList<>(privileges.values());
    }

    @Override
    public List<SecurityPrivilegeRecord> listPrivilegesForRoles(List<String> roleIds) {
      listPrivilegesForRolesCalls++;
      return List.of();
    }

    @Override
    public List<SecurityRepositoryTargetRecord> listRepositoryTargets() {
      return List.of();
    }
  }

  private static final class InMemoryBroadcaster implements CatalogCacheBroadcaster {
    private final Map<String, List<Runnable>> listeners = new LinkedHashMap<>();
    private int publishCalls;

    @Override
    public void subscribe(String catalogName, Runnable refreshListener) {
      listeners.computeIfAbsent(catalogName, ignored -> new ArrayList<>()).add(refreshListener);
    }

    @Override
    public void publishRefresh(String catalogName) {
      publishCalls++;
      List.copyOf(listeners.getOrDefault(catalogName, List.of())).forEach(Runnable::run);
    }
  }
}
