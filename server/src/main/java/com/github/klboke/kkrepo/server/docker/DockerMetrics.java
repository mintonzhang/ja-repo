package com.github.klboke.kkrepo.server.docker;

import com.github.klboke.kkrepo.server.maven.RepositoryRuntime;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DockerMetrics {
  private final MeterRegistry registry;

  @Autowired
  public DockerMetrics(MeterRegistry registry, DockerTransferLimiter transferLimiter) {
    this.registry = registry;
    gauge("kkrepo_docker_uploads_active", "Active Docker upload requests", transferLimiter,
        snapshot -> snapshot.activeUploads());
    gauge("kkrepo_docker_downloads_active", "Active Docker blob download requests", transferLimiter,
        snapshot -> snapshot.activeDownloads());
    gauge("kkrepo_docker_uploads_limit", "Configured Docker upload request concurrency limit", transferLimiter,
        snapshot -> snapshot.maxConcurrentUploads());
    gauge("kkrepo_docker_downloads_limit", "Configured Docker blob download request concurrency limit", transferLimiter,
        snapshot -> snapshot.maxConcurrentDownloads());
  }

  public void uploadSession(RepositoryRuntime runtime, String action, String outcome) {
    counter("kkrepo_docker_upload_sessions_total", "Docker upload session operations",
        runtimeTags(runtime).and("action", tag(action), "outcome", tag(outcome))).increment();
  }

  public void mount(RepositoryRuntime target, RepositoryRuntime source, String outcome) {
    counter("kkrepo_docker_blob_mount_total", "Docker cross-repository blob mount attempts",
        runtimeTags(target)
            .and("source_repo", source == null ? "none" : tag(source.name()))
            .and("outcome", tag(outcome))).increment();
  }

  public void cache(String cache, RepositoryRuntime runtime, String result) {
    counter("kkrepo_docker_cache_events_total", "Docker cache decisions",
        runtimeTags(runtime).and("cache", tag(cache), "result", tag(result))).increment();
  }

  public void digestVerification(RepositoryRuntime runtime, String target, String outcome) {
    counter("kkrepo_docker_digest_verifications_total", "Docker digest verification results",
        runtimeTags(runtime).and("target", tag(target), "outcome", tag(outcome))).increment();
  }

  public void cleanup(RepositoryRuntime runtime, String policy, String action, String outcome, long count) {
    if (count <= 0) {
      return;
    }
    counter("kkrepo_docker_cleanup_items_total", "Docker cleanup policy items",
        runtimeTags(runtime).and("policy", tag(policy), "action", tag(action), "outcome", tag(outcome)))
        .increment(count);
  }

  public void referrers(RepositoryRuntime runtime, String outcome, long count) {
    counter("kkrepo_docker_referrers_total", "Docker referrers API responses",
        runtimeTags(runtime).and("outcome", tag(outcome))).increment();
    if (count > 0) {
      counter("kkrepo_docker_referrer_descriptors_total", "Docker referrer descriptors returned",
          runtimeTags(runtime).and("outcome", tag(outcome))).increment(count);
    }
  }

  private <T> void gauge(
      String name,
      String description,
      DockerTransferLimiter transferLimiter,
      ToDoubleFunction<DockerTransferLimiter.Snapshot> value) {
    Gauge.builder(name, transferLimiter, limiter -> value.applyAsDouble(limiter.snapshot()))
        .description(description)
        .register(registry);
  }

  private Counter counter(String name, String description, Tags tags) {
    return Counter.builder(name)
        .description(description)
        .tags(tags)
        .register(registry);
  }

  private static Tags runtimeTags(RepositoryRuntime runtime) {
    return Tags.of(
        "repo", runtime == null ? "unknown" : tag(runtime.name()),
        "type", runtime == null || runtime.type() == null ? "unknown" : tag(runtime.type().name()));
  }

  private static String tag(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
