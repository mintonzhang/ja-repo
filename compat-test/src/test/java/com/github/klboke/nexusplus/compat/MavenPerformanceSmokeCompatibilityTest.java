package com.github.klboke.nexusplus.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Opt-in performance smoke checks for local compatibility runs.
 *
 * <p>These are intentionally not a load test. They catch obvious latency regressions on warmed
 * single-request paths while keeping normal unit-test runs deterministic by skipping unless
 * {@code COMPAT_PERF_ENABLED=true}.
 */
class MavenPerformanceSmokeCompatibilityTest {
  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  @Test
  void mavenReadLatencyIsWithinConfiguredBudgetWhenEnabled() throws Exception {
    assumeTrue(perfEnabled(), "Set COMPAT_PERF_ENABLED=true to run performance smoke checks");
    String nexusPlusBaseUrl = CompatDefaults.nexusPlusBaseUrl().orElseThrow();
    String repository = setting("compat.nexusPlus.readRepository", "NEXUS_PLUS_COMPAT_READ_REPOSITORY")
        .orElse("maven-public");
    String path = setting("compat.read.path", "COMPAT_READ_PATH")
        .orElse("junit/junit/4.13.2/junit-4.13.2.pom");
    int warmups = intSetting("compat.perf.warmups", "COMPAT_PERF_WARMUPS", 2);
    int samples = intSetting("compat.perf.samples", "COMPAT_PERF_SAMPLES", 8);
    long maxMillis = longSetting("compat.perf.mavenMaxMillis", "COMPAT_PERF_MAVEN_MAX_MILLIS", 1000L);

    long candidateP95 = measureP95(nexusPlusBaseUrl + "/repository/" + repository + "/" + path, warmups, samples);
    assertTrue(candidateP95 <= maxMillis,
        "nexus-plus Maven read p95 " + candidateP95 + "ms exceeded budget " + maxMillis + "ms");

    Optional<String> nexusBaseUrl = CompatDefaults.nexusBaseUrl();
    if (nexusBaseUrl.isPresent()) {
      String referenceRepository = setting("compat.nexus.readRepository", "NEXUS_COMPAT_READ_REPOSITORY")
          .orElse("maven-public");
      long referenceP95 = measureP95(nexusBaseUrl.get() + "/repository/" + referenceRepository + "/" + path,
          warmups, samples);
      double maxRatio = doubleSetting("compat.perf.maxNexusRatio", "COMPAT_PERF_MAX_NEXUS_RATIO", 5.0d);
      long absoluteSlackMillis = longSetting("compat.perf.absoluteSlackMillis",
          "COMPAT_PERF_ABSOLUTE_SLACK_MILLIS", 150L);
      long relativeBudget = (long) Math.ceil(referenceP95 * maxRatio + absoluteSlackMillis);
      assertTrue(candidateP95 <= relativeBudget,
          "nexus-plus Maven read p95 " + candidateP95 + "ms exceeded Nexus-relative budget "
              + relativeBudget + "ms; reference p95=" + referenceP95 + "ms");
    }
  }

  @Test
  void componentSearchLatencyIsWithinConfiguredBudgetWhenEnabled() throws Exception {
    assumeTrue(perfEnabled(), "Set COMPAT_PERF_ENABLED=true to run performance smoke checks");
    String nexusPlusBaseUrl = CompatDefaults.nexusPlusBaseUrl().orElseThrow();
    int warmups = intSetting("compat.perf.warmups", "COMPAT_PERF_WARMUPS", 2);
    int samples = intSetting("compat.perf.samples", "COMPAT_PERF_SAMPLES", 8);
    long maxMillis = longSetting("compat.perf.searchMaxMillis", "COMPAT_PERF_SEARCH_MAX_MILLIS", 500L);

    long p95 = measureP95(nexusPlusBaseUrl + "/internal/search/components?format=maven2&limit=300",
        warmups, samples);
    assertTrue(p95 <= maxMillis,
        "nexus-plus component search p95 " + p95 + "ms exceeded budget " + maxMillis + "ms");
  }

  private static long measureP95(String url, int warmups, int samples) throws Exception {
    int safeWarmups = Math.max(0, Math.min(warmups, 20));
    int safeSamples = Math.max(1, Math.min(samples, 100));
    for (int i = 0; i < safeWarmups; i++) {
      timedGet(url);
    }
    List<Long> timings = new ArrayList<>();
    for (int i = 0; i < safeSamples; i++) {
      timings.add(timedGet(url));
    }
    Collections.sort(timings);
    int index = Math.max(0, (int) Math.ceil(timings.size() * 0.95d) - 1);
    return timings.get(index);
  }

  private static long timedGet(String url) throws Exception {
    long start = System.nanoTime();
    HttpResponse<byte[]> response = HTTP.send(
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "nexus-plus-compat-perf/1")
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
    long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
    assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
        url + " expected 2xx but got " + response.statusCode());
    assertTrue(response.body().length > 0, url + " returned an empty body");
    return elapsedMillis;
  }

  private static boolean perfEnabled() {
    return Boolean.parseBoolean(setting("compat.perf.enabled", "COMPAT_PERF_ENABLED").orElse("false"));
  }

  private static int intSetting(String property, String env, int fallback) {
    return setting(property, env).map(Integer::parseInt).orElse(fallback);
  }

  private static long longSetting(String property, String env, long fallback) {
    return setting(property, env).map(Long::parseLong).orElse(fallback);
  }

  private static double doubleSetting(String property, String env, double fallback) {
    return setting(property, env).map(Double::parseDouble).orElse(fallback);
  }

  private static Optional<String> setting(String property, String env) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      value = System.getenv(env);
    }
    return value == null || value.isBlank()
        ? Optional.empty()
        : Optional.of(value.trim());
  }

}
