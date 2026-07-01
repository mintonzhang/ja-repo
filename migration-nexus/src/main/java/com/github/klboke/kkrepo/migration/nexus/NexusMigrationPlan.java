package com.github.klboke.kkrepo.migration.nexus;

import java.util.List;

public record NexusMigrationPlan(
    String adapter,
    String profileHash,
    String planHash,
    List<NexusMigrationPlanItem> items,
    List<String> warnings,
    List<String> manualActions) {

  public NexusMigrationPlan {
    items = items == null ? List.of() : List.copyOf(items);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    manualActions = manualActions == null ? List.of() : List.copyOf(manualActions);
  }

  public enum SupportStatus {
    FULL,
    CONFIG_ONLY,
    DATA_ONLY,
    UNSUPPORTED,
    NEEDS_MANUAL_ACTION
  }

  public record NexusMigrationPlanItem(
      String area,
      String name,
      String format,
      String type,
      SupportStatus status,
      String sourceAdapter,
      String formatAdapter,
      String readMode,
      String writeMode,
      String checksumMode,
      String resumeKey,
      List<String> reasons,
      List<String> warnings) {

    public NexusMigrationPlanItem {
      reasons = reasons == null ? List.of() : List.copyOf(reasons);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }
}
