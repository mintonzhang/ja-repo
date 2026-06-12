package com.github.klboke.nexusplus.protocol.maven;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.time.ZoneOffset;

public final class MavenConstants {
  private MavenConstants() {}

  public static final String METADATA_FILENAME = "maven-metadata.xml";
  public static final String ARCHETYPE_CATALOG_FILENAME = "archetype-catalog.xml";

  public static final String SNAPSHOT_VERSION_SUFFIX = "SNAPSHOT";

  public static final String DOTTED_TIMESTAMP_VERSION_FORMAT = "yyyyMMdd.HHmmss";

  public static final DateTimeFormatter METADATA_DOTTED_TIMESTAMP =
      DateTimeFormatter.ofPattern(DOTTED_TIMESTAMP_VERSION_FORMAT, Locale.ENGLISH).withZone(ZoneOffset.UTC);

  public static final DateTimeFormatter METADATA_DOTLESS_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ENGLISH).withZone(ZoneOffset.UTC);

  public static final String INDEX_FILE_BASE_PATH = ".index/nexus-maven-repository-index";
  public static final String INDEX_PROPERTY_FILE_PATH = INDEX_FILE_BASE_PATH + ".properties";
  public static final String INDEX_MAIN_CHUNK_FILE_PATH = INDEX_FILE_BASE_PATH + ".gz";

  public static final String CHECKSUM_CONTENT_TYPE = "text/plain";
}
