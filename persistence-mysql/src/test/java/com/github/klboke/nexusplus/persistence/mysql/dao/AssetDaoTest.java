package com.github.klboke.nexusplus.persistence.mysql.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AssetDaoTest {
  @Test
  void reusableBlobIdSqlSeparatesDeletedPredicateWithoutGapLocking() {
    String liveSql = AssetDao.reusableBlobIdSql(false);
    String deletedOnlySql = AssetDao.reusableBlobIdSql(true);

    assertFalse(liveSql.contains("ANDdeleted_at"));
    assertFalse(deletedOnlySql.contains("ANDdeleted_at"));
    assertFalse(liveSql.contains("NULLORDER"));
    assertFalse(deletedOnlySql.contains("NULLORDER"));
    assertFalse(liveSql.contains("FOR UPDATE"));
    assertFalse(deletedOnlySql.contains("FOR UPDATE"));
    assertTrue(liveSql.contains("SELECT id"));
    assertTrue(liveSql.contains("AND deleted_at IS NULL\n"));
    assertTrue(deletedOnlySql.contains("AND deleted_at IS NOT NULL\n"));
  }
}
