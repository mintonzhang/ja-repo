package com.github.klboke.nexusplus.server.metrics;

import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryType;
import com.github.klboke.nexusplus.server.maven.RepositoryRuntime;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class NexusPlusMetrics {
  private static final Duration[] LATENCY_SLOS = {
      Duration.ofMillis(10),
      Duration.ofMillis(25),
      Duration.ofMillis(50),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofSeconds(1),
      Duration.ofSeconds(2),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(30)
  };

  private final MeterRegistry registry;

  public NexusPlusMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public Timer.Sample startTimer() {
    return Timer.start(registry);
  }

  public void recordRepositoryRequest(
      String repository,
      String format,
      String type,
      String method,
      String operation,
      int status,
      long requestBytes,
      long responseBytes,
      Throwable failure,
      Timer.Sample sample) {
    Tags tags = Tags.of(
        "repo", tagValue(repository),
        "format", tagValue(format),
        "type", tagValue(type),
        "method", tagValue(method),
        "operation", tagValue(operation),
        "status", Integer.toString(Math.max(0, status)),
        "outcome", outcome(status, failure));
    Counter.builder("nexus_plus_repository_requests_total")
        .description("Repository HTTP requests by repository, format, type, operation, and status")
        .tags(tags)
        .register(registry)
        .increment();
    sample.stop(Timer.builder("nexus_plus_repository_request_duration")
        .description("Repository HTTP request duration")
        .tags(tags)
        .serviceLevelObjectives(LATENCY_SLOS)
        .publishPercentileHistogram()
        .register(registry));
    recordBytes("nexus_plus_repository_upload_bytes_total",
        "Repository request body bytes", requestBytes, tags);
    recordBytes("nexus_plus_repository_response_bytes_total",
        "Repository response body bytes from Content-Length headers", responseBytes, tags);
  }

  public void recordBlobOperation(
      String store,
      String type,
      String engine,
      String operation,
      String outcome,
      Timer.Sample sample) {
    Tags tags = Tags.of(
        "store", tagValue(store),
        "type", tagValue(type),
        "engine", tagValue(engine),
        "op", tagValue(operation),
        "outcome", tagValue(outcome));
    Counter.builder("nexus_plus_blob_storage_operations_total")
        .description("Blob storage operations by store and backend")
        .tags(tags)
        .register(registry)
        .increment();
    sample.stop(Timer.builder("nexus_plus_blob_storage_operation_duration")
        .description("Blob storage operation duration")
        .tags(tags)
        .serviceLevelObjectives(LATENCY_SLOS)
        .publishPercentileHistogram()
        .register(registry));
  }

  public void recordBlobBytes(String store, String type, String engine, String operation, long bytes) {
    Tags tags = Tags.of(
        "store", tagValue(store),
        "type", tagValue(type),
        "engine", tagValue(engine),
        "op", tagValue(operation));
    recordBytes("nexus_plus_blob_storage_bytes_total",
        "Blob storage bytes read or written", bytes, tags);
  }

  public void recordWorkerBatch(String worker, String outcome, Timer.Sample sample) {
    Tags tags = Tags.of("worker", tagValue(worker), "outcome", tagValue(outcome));
    sample.stop(Timer.builder("nexus_plus_worker_batch_duration")
        .description("Background worker batch duration")
        .tags(tags)
        .serviceLevelObjectives(LATENCY_SLOS)
        .publishPercentileHistogram()
        .register(registry));
  }

  public void recordWorkerItem(String worker, String kind, String outcome, Timer.Sample sample) {
    Tags tags = Tags.of(
        "worker", tagValue(worker),
        "kind", tagValue(kind),
        "outcome", tagValue(outcome));
    Counter.builder("nexus_plus_worker_items_total")
        .description("Background worker items processed")
        .tags(tags)
        .register(registry)
        .increment();
    sample.stop(Timer.builder("nexus_plus_worker_item_duration")
        .description("Background worker item duration")
        .tags(tags)
        .serviceLevelObjectives(LATENCY_SLOS)
        .publishPercentileHistogram()
        .register(registry));
  }

  public void incrementWorkerItems(String worker, String kind, String outcome, long count) {
    if (count <= 0) return;
    Counter.builder("nexus_plus_worker_items_total")
        .description("Background worker items processed")
        .tags("worker", tagValue(worker), "kind", tagValue(kind), "outcome", tagValue(outcome))
        .register(registry)
        .increment(count);
  }

  public void incrementBlobGcDeletedBytes(long bytes) {
    recordBytes("nexus_plus_blob_gc_deleted_bytes_total", "Blob bytes deleted by GC", bytes, Tags.empty());
  }

  public void incrementBlobGcReconcile(long scanned, long marked) {
    recordBytes("nexus_plus_blob_gc_reconcile_scanned_total",
        "Blob rows scanned by orphan reconcile", scanned, Tags.empty());
    recordBytes("nexus_plus_blob_gc_reconcile_marked_total",
        "Unreferenced blobs marked for GC", marked, Tags.empty());
  }

  public void recordProxyCacheEvent(RepositoryRuntime runtime, String result) {
    if (runtime == null) return;
    Counter.builder("nexus_plus_proxy_cache_events_total")
        .description("Proxy cache decisions by repository")
        .tags(
            "repo", tagValue(runtime.name()),
            "format", format(runtime.format()),
            "type", type(runtime.type()),
            "result", tagValue(result))
        .register(registry)
        .increment();
  }

  public void recordProxyRemote(
      String repository,
      String format,
      String method,
      String remoteHost,
      int status,
      Throwable failure,
      Timer.Sample sample) {
    Tags tags = Tags.of(
        "repo", tagValue(repository),
        "format", tagValue(format),
        "method", tagValue(method),
        "remote_host", tagValue(remoteHost),
        "status", Integer.toString(Math.max(0, status)),
        "outcome", outcome(status, failure));
    Counter.builder("nexus_plus_proxy_remote_requests_total")
        .description("Proxy upstream HTTP requests")
        .tags(tags)
        .register(registry)
        .increment();
    sample.stop(Timer.builder("nexus_plus_proxy_remote_duration")
        .description("Proxy upstream HTTP request duration")
        .tags(tags)
        .serviceLevelObjectives(LATENCY_SLOS)
        .publishPercentileHistogram()
        .register(registry));
  }

  public void recordRateLimitBlocked(String type) {
    Counter.builder("nexus_plus_rate_limit_blocked_total")
        .description("Requests blocked by shared rate limit")
        .tags("type", tagValue(type))
        .register(registry)
        .increment();
  }

  private void recordBytes(String name, String description, long bytes, Tags tags) {
    if (bytes <= 0) return;
    Counter.builder(name)
        .description(description)
        .tags(tags)
        .register(registry)
        .increment(bytes);
  }

  public static String format(RepositoryFormat format) {
    return format == null ? "unknown" : tagValue(format.name());
  }

  public static String type(RepositoryType type) {
    return type == null ? "unknown" : tagValue(type.name());
  }

  public static String tagValue(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private static String outcome(int status, Throwable failure) {
    if (failure != null) {
      return "error";
    }
    if (status >= 500) {
      return "server_error";
    }
    if (status >= 400) {
      return "client_error";
    }
    if (status >= 300) {
      return "redirection";
    }
    return "success";
  }
}
