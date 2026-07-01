package com.github.klboke.kkrepo.migration.nexus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class MigrationPlanHashes {
  private static final ObjectMapper HASH_MAPPER = new ObjectMapper()
      .findAndRegisterModules()
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private MigrationPlanHashes() {
  }

  static String profileHash(NexusSourceProfile profile) {
    if (profile == null) {
      return sha256("{}");
    }
    return sha256(canonical(java.util.Map.of(
        "nexusVersion", value(profile.nexusVersion()),
        "scriptApi", profile.scriptApi(),
        "metadataEngine", profile.metadataEngine(),
        "repositoryModel", profile.repositoryModel(),
        "securityModel", profile.securityModel(),
        "blobModel", profile.blobModel(),
        "repositories", profile.repositories(),
        "formatCapabilities", profile.formatCapabilities(),
        "warnings", profile.warnings())));
  }

  static String planHash(
      String adapter,
      List<NexusMigrationPlan.NexusMigrationPlanItem> items,
      List<String> warnings,
      List<String> manualActions) {
    return sha256(canonical(java.util.Map.of(
        "adapter", adapter == null ? "" : adapter,
        "items", items == null ? List.of() : items,
        "warnings", warnings == null ? List.of() : warnings,
        "manualActions", manualActions == null ? List.of() : manualActions)));
  }

  private static String canonical(Object value) {
    try {
      return HASH_MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to canonicalize migration hash input", e);
    }
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 digest is unavailable", e);
    }
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}
