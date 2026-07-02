package com.github.klboke.kkrepo.server.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.klboke.kkrepo.core.RepositoryFormat;
import com.github.klboke.kkrepo.core.RepositoryType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryRuntimeTest {

  @Test
  void effectiveMaxAgeUsesShortestFiniteMemberValue() {
    RepositoryRuntime proxy = repository(2L, RepositoryType.PROXY, 1, 5, List.of());
    RepositoryRuntime hosted = repository(3L, RepositoryType.HOSTED, null, null, List.of());
    RepositoryRuntime group = repository(1L, RepositoryType.GROUP, 1440, 1440, List.of(proxy, hosted));

    assertEquals(1, group.effectiveContentMaxAgeMinutesOrDefault());
    assertEquals(5, group.effectiveMetadataMaxAgeMinutesOrDefault());
  }

  @Test
  void effectiveMaxAgeWalksNestedGroups() {
    RepositoryRuntime proxy = repository(3L, RepositoryType.PROXY, 3, 2, List.of());
    RepositoryRuntime inner = repository(2L, RepositoryType.GROUP, 60, 60, List.of(proxy));
    RepositoryRuntime outer = repository(1L, RepositoryType.GROUP, 1440, 1440, List.of(inner));

    assertEquals(3, outer.effectiveContentMaxAgeMinutesOrDefault());
    assertEquals(2, outer.effectiveMetadataMaxAgeMinutesOrDefault());
  }

  @Test
  void disabledAgeDoesNotMaskFiniteMemberValue() {
    RepositoryRuntime proxy = repository(2L, RepositoryType.PROXY, 1, 1, List.of());
    RepositoryRuntime group = repository(1L, RepositoryType.GROUP, -1, -1, List.of(proxy));

    assertEquals(1, group.effectiveContentMaxAgeMinutesOrDefault());
    assertEquals(1, group.effectiveMetadataMaxAgeMinutesOrDefault());
  }

  @Test
  void allDisabledAgesRemainDisabled() {
    RepositoryRuntime proxy = repository(2L, RepositoryType.PROXY, -1, -1, List.of());
    RepositoryRuntime group = repository(1L, RepositoryType.GROUP, -1, -1, List.of(proxy));

    assertEquals(-1, group.effectiveContentMaxAgeMinutesOrDefault());
    assertEquals(-1, group.effectiveMetadataMaxAgeMinutesOrDefault());
  }

  @Test
  void groupCycleDoesNotRecurseForever() {
    List<RepositoryRuntime> rootMembers = new ArrayList<>();
    List<RepositoryRuntime> nestedMembers = new ArrayList<>();
    RepositoryRuntime root = repository(1L, RepositoryType.GROUP, 1440, 1440, rootMembers);
    RepositoryRuntime nested = repository(2L, RepositoryType.GROUP, -1, -1, nestedMembers);
    rootMembers.add(nested);
    nestedMembers.add(root);
    nestedMembers.add(repository(3L, RepositoryType.PROXY, 10, 8, List.of()));

    assertEquals(10, root.effectiveContentMaxAgeMinutesOrDefault());
    assertEquals(8, root.effectiveMetadataMaxAgeMinutesOrDefault());
  }

  private static RepositoryRuntime repository(
      long id,
      RepositoryType type,
      Integer contentMaxAgeMinutes,
      Integer metadataMaxAgeMinutes,
      List<RepositoryRuntime> members) {
    return new RepositoryRuntime(
        id,
        "repo-" + id,
        RepositoryFormat.MAVEN2,
        type,
        "maven2-" + type.name().toLowerCase(),
        true,
        1L,
        null,
        "MIXED",
        "PERMISSIVE",
        true,
        type == RepositoryType.PROXY ? "http://example.invalid/repository/repo-" + id + "/" : null,
        contentMaxAgeMinutes,
        metadataMaxAgeMinutes,
        members);
  }
}
