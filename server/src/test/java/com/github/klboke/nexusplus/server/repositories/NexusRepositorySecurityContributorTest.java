package com.github.klboke.nexusplus.server.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.klboke.nexusplus.core.RepositoryFormat;
import org.junit.jupiter.api.Test;

class NexusRepositorySecurityContributorTest {

  @Test
  void buildsNexusFormatViewPrivileges() {
    var privileges = NexusRepositorySecurityContributor.formatViewPrivileges(RepositoryFormat.MAVEN2);

    assertEquals(6, privileges.size());
    assertTrue(privileges.stream()
        .anyMatch(privilege -> "nx-repository-view-maven2-*-read".equals(privilege.privilegeId())));
    assertEquals("maven2", privileges.get(0).properties().get("format"));
    assertEquals("*", privileges.get(0).properties().get("repository"));
    assertEquals(true, privileges.get(0).readOnly());
  }

  @Test
  void buildsRepositoryViewAndAdminPrivilegesWithoutPerRepositoryAdminAdd() {
    var privileges = NexusRepositorySecurityContributor.repositoryPrivileges(RepositoryFormat.NPM, "npm-hosted");

    assertEquals(11, privileges.size());
    assertTrue(privileges.stream()
        .anyMatch(privilege -> "nx-repository-view-npm-npm-hosted-add".equals(privilege.privilegeId())));
    assertTrue(privileges.stream()
        .anyMatch(privilege -> "nx-repository-admin-npm-npm-hosted-edit".equals(privilege.privilegeId())));
    assertFalse(privileges.stream()
        .anyMatch(privilege -> "nx-repository-admin-npm-npm-hosted-add".equals(privilege.privilegeId())));
  }
}
