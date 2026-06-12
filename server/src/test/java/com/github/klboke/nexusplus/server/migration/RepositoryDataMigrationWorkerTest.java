package com.github.klboke.nexusplus.server.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.persistence.mysql.dao.RepositoryDataMigrationDao;
import com.github.klboke.nexusplus.persistence.mysql.dao.RepositoryDataMigrationDao.AssetClaim;
import com.github.klboke.nexusplus.persistence.mysql.model.RepositoryDataMigrationAssetRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RepositoryDataMigrationWorkerTest {
  @Test
  void batchProgressTargetsIncludeRepositoryAndJobRows() {
    RepositoryDataMigrationWorker.BatchProgressTargets targets =
        RepositoryDataMigrationWorker.batchProgressTargets(List.of(
            claim(10L, 100L, "com/acme/app/1.0/app-1.0.jar"),
            claim(11L, 100L, "com/acme/lib/1.0/lib-1.0.jar"),
            claim(10L, 100L, "com/acme/app/1.0/app-1.0.pom")));

    assertEquals(List.of(10L, 11L), targets.repositoryJobIds());
    assertEquals(List.of(100L), targets.jobIds());
  }

  private static AssetClaim claim(long repositoryJobId, long migrationJobId, String path) {
    RepositoryDataMigrationAssetRecord asset = new RepositoryDataMigrationAssetRecord(
        null,
        repositoryJobId,
        null,
        null,
        path,
        null,
        RepositoryFormat.MAVEN2,
        "com.acme",
        "app",
        "1.0",
        "artifact",
        "application/octet-stream",
        1L,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        RepositoryDataMigrationDao.ASSET_PENDING,
        0,
        null,
        null,
        null,
        null,
        null,
        null,
        Map.of(),
        null);
    return new AssetClaim(
        asset,
        migrationJobId,
        "source",
        "target",
        1L,
        RepositoryFormat.MAVEN2,
        "http://nexus.example",
        Map.of());
  }
}
