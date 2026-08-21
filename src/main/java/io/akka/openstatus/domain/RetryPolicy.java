package io.akka.openstatus.domain;

/**
 * SPEC-001 rule 4, evidence question-log row 2: {@code cenkalti/backoff}'s
 * {@code NewExponentialBackOff()} defaults — initial 500ms, ×1.5 per attempt, capped at 60s,
 * ±50% jitter — read against {@code apps/checker/pkg/job/http_job.go:70-267} and confirmed
 * passing under the source's own test suite (question-log row 1).
 *
 * <p>Pure and jitter-injected rather than reading a random source directly, so the shape is
 * testable without flakiness.
 */
public final class RetryPolicy {

  public static final long INITIAL_DELAY_MS = 500;
  public static final double MULTIPLIER = 1.5;
  public static final long MAX_DELAY_MS = 60_000;
  public static final double JITTER_FACTOR = 0.5;

  private RetryPolicy() {}

  /** A retry is only attempted while the classification is ERROR and attempts remain. */
  public static boolean shouldRetry(ProbeStatus classification, int attemptNumber, int maxAttempts) {
    return classification == ProbeStatus.ERROR && attemptNumber < maxAttempts;
  }

  /**
   * @param attemptNumber 1-based: the delay before the *next* attempt after this one.
   * @param jitterFraction in [-1, 1]; the source's randomization factor is 0.5, applied as
   *     {@code base * (1 + jitterFraction * JITTER_FACTOR)}.
   */
  public static long backoffDelayMs(int attemptNumber, double jitterFraction) {
    double base = INITIAL_DELAY_MS * Math.pow(MULTIPLIER, attemptNumber - 1);
    base = Math.min(base, MAX_DELAY_MS);
    double jittered = base * (1 + jitterFraction * JITTER_FACTOR);
    return Math.round(Math.max(0, jittered));
  }

  public static int effectiveMaxAttempts(int configuredRetry) {
    return configuredRetry <= 0 ? 3 : configuredRetry;
  }
}
