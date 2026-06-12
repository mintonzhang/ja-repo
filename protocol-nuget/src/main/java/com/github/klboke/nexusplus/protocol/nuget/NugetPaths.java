package com.github.klboke.nexusplus.protocol.nuget;

import java.util.Locale;

public final class NugetPaths {
  public static final String SERVICE_INDEX = "index.json";
  public static final String PACKAGE_PUBLISH = "api/v2/package";

  private NugetPaths() {
  }

  public static String normalizePackageId(String id) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("NuGet package id is required");
    }
    return id.trim().toLowerCase(Locale.ROOT);
  }

  public static String normalizeVersion(String version) {
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("NuGet package version is required");
    }
    return version.trim().toLowerCase(Locale.ROOT);
  }

  public static String flatContainerVersionIndex(String packageId) {
    return "v3-flatcontainer/" + normalizePackageId(packageId) + "/index.json";
  }

  public static String flatContainerPackage(String packageId, String version) {
    String id = normalizePackageId(packageId);
    String v = normalizeVersion(version);
    return "v3-flatcontainer/" + id + "/" + v + "/" + id + "." + v + ".nupkg";
  }

  public static String flatContainerNuspec(String packageId, String version) {
    String id = normalizePackageId(packageId);
    String v = normalizeVersion(version);
    return "v3-flatcontainer/" + id + "/" + v + "/" + id + ".nuspec";
  }

  public static String registrationIndex(String packageId) {
    return "v3/registration5-semver1/" + normalizePackageId(packageId) + "/index.json";
  }

  public static String hostedPackagePath(String packageId, String version) {
    return "packages/" + normalizePackageId(packageId) + "/" + normalizeVersion(version) + ".nupkg";
  }
}
