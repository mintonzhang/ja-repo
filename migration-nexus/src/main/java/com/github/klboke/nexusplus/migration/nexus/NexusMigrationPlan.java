package com.github.klboke.nexusplus.migration.nexus;

import java.nio.file.Path;

public record NexusMigrationPlan(
    Path nexusDataDirectory,
    boolean migrateProxyArtifacts,
    boolean dryRun) {
}
