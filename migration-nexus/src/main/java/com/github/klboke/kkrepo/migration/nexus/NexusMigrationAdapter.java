package com.github.klboke.kkrepo.migration.nexus;

import com.github.klboke.kkrepo.migration.nexus.NexusMigrationPlan.SupportStatus;

interface NexusMigrationAdapter {
  String name();

  default String repositoryReadMode(
      NexusSourceProfile profile,
      String format,
      String type,
      boolean migrateProxyArtifacts) {
    return repositoryReadMode(format, type, migrateProxyArtifacts);
  }

  String repositoryReadMode(String format, String type, boolean migrateProxyArtifacts);

  default String checksumMode(
      NexusSourceProfile profile,
      String format,
      String type,
      boolean migrateProxyArtifacts) {
    return checksumMode(format, type, migrateProxyArtifacts);
  }

  String checksumMode(String format, String type, boolean migrateProxyArtifacts);

  default SupportStatus repositoryStatus(
      NexusSourceProfile profile,
      String format,
      String type,
      boolean migrateProxyArtifacts) {
    return repositoryStatus(format, type, migrateProxyArtifacts);
  }

  SupportStatus repositoryStatus(String format, String type, boolean migrateProxyArtifacts);
}
