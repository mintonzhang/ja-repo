package com.github.klboke.nexusplus.persistence.mysql.dao;

import static com.github.klboke.nexusplus.persistence.mysql.support.JdbcRows.nullableInstant;

import com.github.klboke.nexusplus.persistence.mysql.model.MigrationJobRecord;
import com.github.klboke.nexusplus.persistence.mysql.support.JdbcInserts;
import com.github.klboke.nexusplus.persistence.mysql.support.JsonColumns;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MigrationJobDao {
  private final JdbcTemplate jdbcTemplate;
  private final JsonColumns jsonColumns;
  private final RowMapper<MigrationJobRecord> rowMapper;

  public MigrationJobDao(JdbcTemplate jdbcTemplate, JsonColumns jsonColumns) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonColumns = jsonColumns;
    this.rowMapper = (rs, rowNum) -> new MigrationJobRecord(
        rs.getLong("id"),
        rs.getString("source_nexus_version"),
        rs.getString("source_data_path"),
        rs.getString("status"),
        jsonColumns.read(rs.getString("options_json")),
        jsonColumns.read(rs.getString("summary_json")),
        nullableInstant(rs, "started_at"),
        nullableInstant(rs, "finished_at"));
  }

  public long create(String sourceNexusVersion, String sourceDataPath, Map<String, Object> options) {
    return JdbcInserts.insert(jdbcTemplate, """
        INSERT INTO migration_job
          (source_nexus_version, source_data_path, status, options_json)
        VALUES (?, ?, 'running', ?)
        """, ps -> {
      ps.setString(1, sourceNexusVersion);
      ps.setString(2, sourceDataPath);
      ps.setString(3, jsonColumns.write(options));
    });
  }

  public Optional<MigrationJobRecord> findById(long id) {
    return jdbcTemplate.query("SELECT * FROM migration_job WHERE id = ?", rowMapper, id)
        .stream()
        .findFirst();
  }

  public void markFinished(long id, String status, Map<String, Object> summary) {
    jdbcTemplate.update("""
        UPDATE migration_job
        SET status = ?, summary_json = ?, finished_at = CURRENT_TIMESTAMP(3)
        WHERE id = ?
        """, status, jsonColumns.write(summary), id);
  }
}
