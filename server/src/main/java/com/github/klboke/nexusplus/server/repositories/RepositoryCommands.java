package com.github.klboke.nexusplus.server.repositories;

import java.util.List;

/**
 * Plain request payloads used by {@link RepositoryService}. Kept in one file because they
 * are tiny and only meaningful together.
 */
public final class RepositoryCommands {
  private RepositoryCommands() {
  }

  public record CreateCommand(
      String name,
      String recipe,
      Boolean online,
      String blobStoreName,
      Boolean strictContentTypeValidation,
      HostedSettings hosted,
      ProxySettings proxy,
      RawSettings raw,
      GroupSettings group) {
  }

  public record UpdateCommand(
      Boolean online,
      String blobStoreName,
      Boolean strictContentTypeValidation,
      HostedSettings hosted,
      ProxySettings proxy,
      RawSettings raw,
      GroupSettings group) {
  }

  public record HostedSettings(
      String writePolicy,
      String versionPolicy,
      String layoutPolicy) {
  }

  public record ProxySettings(
      String remoteUrl,
      Integer contentMaxAgeMinutes,
      Integer metadataMaxAgeMinutes,
      Boolean autoBlock) {
  }

  public record RawSettings(
      String contentDisposition) {
  }

  public record GroupSettings(
      List<String> memberNames) {
  }
}
