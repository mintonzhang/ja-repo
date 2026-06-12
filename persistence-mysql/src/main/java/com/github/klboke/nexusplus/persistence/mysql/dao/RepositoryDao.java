package com.github.klboke.nexusplus.persistence.mysql.dao;

import static com.github.klboke.nexusplus.persistence.mysql.support.JdbcRows.nullableLong;

import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryType;
import com.github.klboke.nexusplus.persistence.mysql.model.RepositoryRecord;
import com.github.klboke.nexusplus.persistence.mysql.support.EnumColumns;
import com.github.klboke.nexusplus.persistence.mysql.support.JdbcInserts;
import com.github.klboke.nexusplus.persistence.mysql.support.JsonColumns;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RepositoryDao {
  private final JdbcTemplate jdbcTemplate;
  private final JsonColumns jsonColumns;
  private final RowMapper<RepositoryRecord> rowMapper;

  public RepositoryDao(JdbcTemplate jdbcTemplate, JsonColumns jsonColumns) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonColumns = jsonColumns;
    this.rowMapper = (rs, rowNum) -> new RepositoryRecord(
        rs.getLong("id"),
        rs.getString("name"),
        EnumColumns.read(RepositoryFormat.class, rs.getString("format")),
        EnumColumns.read(RepositoryType.class, rs.getString("type")),
        rs.getString("recipe_name"),
        rs.getBoolean("online"),
        nullableLong(rs, "blob_store_id"),
        nullableLong(rs, "routing_rule_id"),
        rs.getString("proxy_remote_url"),
        rs.getString("version_policy"),
        rs.getString("layout_policy"),
        rs.getString("write_policy"),
        rs.getBoolean("strict_content_type_validation"),
        jsonColumns.read(rs.getString("attributes_json")));
  }

  public long insert(RepositoryRecord record) {
    return JdbcInserts.insert(jdbcTemplate, """
        INSERT INTO repository
          (name, format, type, recipe_name, online, blob_store_id, routing_rule_id,
           proxy_remote_url, version_policy, layout_policy, write_policy,
           strict_content_type_validation, attributes_json)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, ps -> {
      ps.setString(1, record.name());
      ps.setString(2, EnumColumns.write(record.format()));
      ps.setString(3, EnumColumns.write(record.type()));
      ps.setString(4, record.recipeName());
      ps.setBoolean(5, record.online());
      ps.setObject(6, record.blobStoreId());
      ps.setObject(7, record.routingRuleId());
      ps.setString(8, record.proxyRemoteUrl());
      ps.setString(9, record.versionPolicy());
      ps.setString(10, record.layoutPolicy());
      ps.setString(11, record.writePolicy());
      ps.setBoolean(12, record.strictContentTypeValidation());
      ps.setString(13, jsonColumns.write(record.attributes()));
    });
  }

  public Optional<RepositoryRecord> findById(long id) {
    return jdbcTemplate.query("SELECT * FROM repository WHERE id = ?", rowMapper, id)
        .stream()
        .findFirst();
  }

  public Optional<RepositoryRecord> findByName(String name) {
    return jdbcTemplate.query("SELECT * FROM repository WHERE name = ?", rowMapper, name)
        .stream()
        .findFirst();
  }

  public List<RepositoryRecord> list() {
    return jdbcTemplate.query("SELECT * FROM repository ORDER BY name", rowMapper);
  }

  public void update(RepositoryRecord record) {
    jdbcTemplate.update("""
        UPDATE repository
        SET online = ?, blob_store_id = ?, routing_rule_id = ?, proxy_remote_url = ?,
            version_policy = ?, layout_policy = ?, write_policy = ?,
            strict_content_type_validation = ?, attributes_json = ?
        WHERE id = ?
        """,
        record.online(),
        record.blobStoreId(),
        record.routingRuleId(),
        record.proxyRemoteUrl(),
        record.versionPolicy(),
        record.layoutPolicy(),
        record.writePolicy(),
        record.strictContentTypeValidation(),
        jsonColumns.write(record.attributes()),
        record.id());
  }

  public RepositoryRecord upsertByName(RepositoryRecord record) {
    Optional<RepositoryRecord> existing = findByName(record.name());
    if (existing.isPresent()) {
      jdbcTemplate.update("""
          UPDATE repository
          SET format = ?, type = ?, recipe_name = ?, online = ?, blob_store_id = ?,
              routing_rule_id = ?, proxy_remote_url = ?, version_policy = ?,
              layout_policy = ?, write_policy = ?, strict_content_type_validation = ?,
              attributes_json = ?
          WHERE name = ?
          """,
          EnumColumns.write(record.format()),
          EnumColumns.write(record.type()),
          record.recipeName(),
          record.online(),
          record.blobStoreId(),
          record.routingRuleId(),
          record.proxyRemoteUrl(),
          record.versionPolicy(),
          record.layoutPolicy(),
          record.writePolicy(),
          record.strictContentTypeValidation(),
          jsonColumns.write(record.attributes()),
          record.name());
      return findByName(record.name()).orElseThrow();
    }
    long id = insert(record);
    return findById(id).orElseThrow();
  }

  public int deleteById(long id) {
    return jdbcTemplate.update("DELETE FROM repository WHERE id = ?", id);
  }

  public boolean existsByName(String name) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM repository WHERE name = ?", Integer.class, name);
    return count != null && count > 0;
  }

  public void addMember(long groupRepositoryId, long memberRepositoryId, int sortOrder) {
    jdbcTemplate.update("""
        INSERT INTO repository_member (repository_id, member_repository_id, sort_order)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order)
        """, groupRepositoryId, memberRepositoryId, sortOrder);
  }

  public List<RepositoryRecord> listMembers(long groupRepositoryId) {
    return jdbcTemplate.query("""
        SELECT r.*
        FROM repository_member rm
        JOIN repository r ON r.id = rm.member_repository_id
        WHERE rm.repository_id = ?
        ORDER BY rm.sort_order
        """, rowMapper, groupRepositoryId);
  }

  /**
   * Batch-load every group's ordered member names in a single query, keyed by group repository id.
   * Replaces the per-group {@link #listMembers(long)} fan-out when assembling the full catalog.
   */
  public Map<Long, List<String>> listAllGroupMembers() {
    Map<Long, List<String>> membersByGroupId = new LinkedHashMap<>();
    jdbcTemplate.query("""
        SELECT rm.repository_id AS group_id, r.name AS member_name
        FROM repository_member rm
        JOIN repository r ON r.id = rm.member_repository_id
        ORDER BY rm.repository_id, rm.sort_order
        """, rs -> {
          long groupId = rs.getLong("group_id");
          String memberName = rs.getString("member_name");
          membersByGroupId.computeIfAbsent(groupId, ignored -> new ArrayList<>()).add(memberName);
        });
    return membersByGroupId;
  }

  public void clearMembers(long groupRepositoryId) {
    jdbcTemplate.update("DELETE FROM repository_member WHERE repository_id = ?", groupRepositoryId);
  }

  public void replaceMembers(long groupRepositoryId, List<Long> memberRepositoryIds) {
    clearMembers(groupRepositoryId);
    int order = 0;
    for (Long memberId : memberRepositoryIds) {
      addMember(groupRepositoryId, memberId, order++);
    }
  }

  public List<RepositoryRecord> listGroupsContaining(long memberRepositoryId) {
    return jdbcTemplate.query("""
        SELECT r.*
        FROM repository_member rm
        JOIN repository r ON r.id = rm.repository_id
        WHERE rm.member_repository_id = ?
        ORDER BY r.name
        """, rowMapper, memberRepositoryId);
  }

  public List<String> findNamesUsingBlobStore(long blobStoreId) {
    return jdbcTemplate.queryForList(
        "SELECT name FROM repository WHERE blob_store_id = ? ORDER BY name",
        String.class,
        blobStoreId);
  }

  public boolean hasComponents(long repositoryId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM component WHERE repository_id = ?", Integer.class, repositoryId);
    return count != null && count > 0;
  }
}
