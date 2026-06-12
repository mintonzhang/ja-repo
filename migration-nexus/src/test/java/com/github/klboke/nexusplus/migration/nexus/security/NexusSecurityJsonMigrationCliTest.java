package com.github.klboke.nexusplus.migration.nexus.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class NexusSecurityJsonMigrationCliTest {

  @Test
  void parsesRequiredDatabaseAndExportArguments() {
    NexusSecurityJsonMigrationCli.Options options = NexusSecurityJsonMigrationCli.Options.parse(new String[] {
        "--export", "/tmp/security-export.json",
        "--jdbc-url", "jdbc:mysql://127.0.0.1:3306/nexus_plus",
        "--username", "nexus_plus",
        "--password", "secret",
        "--source-nexus-version", "3.29.2-02",
        "--source-data-path", "/nexus-data"
    });

    assertEquals("/tmp/security-export.json", options.exportPath().toString());
    assertEquals("jdbc:mysql://127.0.0.1:3306/nexus_plus", options.jdbcUrl());
    assertEquals("nexus_plus", options.username());
    assertEquals("secret", options.password());
    assertEquals("3.29.2-02", options.sourceNexusVersion());
    assertEquals("/nexus-data", options.sourceDataPath());
  }

  @Test
  void printsUsageWithoutOpeningDatabaseWhenHelpIsRequested() {
    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

    int exitCode = new NexusSecurityJsonMigrationCli().run(
        new String[] {"--help"},
        new PrintStream(outBytes),
        new PrintStream(errBytes));

    assertEquals(0, exitCode);
    assertTrue(outBytes.toString().contains("--export /path/to/security-export.json"));
    assertEquals("", errBytes.toString());
  }
}
