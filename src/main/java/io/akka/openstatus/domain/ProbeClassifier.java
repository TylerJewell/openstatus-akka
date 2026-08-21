package io.akka.openstatus.domain;

/**
 * SPEC-001 rule 3, evidence question-log row 3 (read against
 * {@code apps/checker/pkg/job/http_job.go:156-249}, confirmed passing under the source's own
 * test suite in question-log row 1).
 *
 * <p>Assertions beyond the default 2xx check are out of scope (SPEC-001 §1) — this classifies
 * only status code and latency.
 */
public final class ProbeClassifier {

  private ProbeClassifier() {}

  public static boolean isSuccessStatus(int statusCode) {
    return statusCode >= 200 && statusCode < 300;
  }

  public static ProbeStatus classify(AttemptResult attempt, Long degradedAfterMs) {
    if (attempt.transportError() || !isSuccessStatus(attempt.statusCode())) {
      return ProbeStatus.ERROR;
    }
    if (degradedAfterMs != null && degradedAfterMs > 0 && attempt.latencyMs() > degradedAfterMs) {
      return ProbeStatus.DEGRADED;
    }
    return ProbeStatus.ACTIVE;
  }
}
