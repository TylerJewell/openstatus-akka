package io.akka.openstatus.domain;

import java.util.List;

/**
 * What a monitor was registered with — SPEC-001 §2, rules 1-4.
 *
 * <p>{@code degradedAfterMs} is nullable: {@code null} means "never classify degraded on
 * latency alone" (question-log row 3), matching the source's nullable {@code degradedAfter}
 * column rather than defaulting it to a magic number.
 */
public record MonitorConfig(
    String url,
    List<String> regions,
    long periodicityMs,
    long timeoutMs,
    int retry,
    Long degradedAfterMs,
    boolean followRedirects) {

  public MonitorConfig {
    if (regions == null || regions.isEmpty()) {
      throw new IllegalArgumentException("a monitor needs at least one region");
    }
    regions = List.copyOf(regions);
  }

  public int regionCount() {
    return regions.size();
  }
}
