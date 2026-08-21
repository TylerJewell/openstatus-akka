package io.akka.openstatus.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** SPEC-001 rule 4, conformance row 2. */
public class RetryPolicyTest {

  @Test
  public void errorRetriesWhileAttemptsRemain() {
    assertThat(RetryPolicy.shouldRetry(ProbeStatus.ERROR, 1, 3)).isTrue();
    assertThat(RetryPolicy.shouldRetry(ProbeStatus.ERROR, 2, 3)).isTrue();
  }

  @Test
  public void errorDoesNotRetryOnTheLastAttempt() {
    assertThat(RetryPolicy.shouldRetry(ProbeStatus.ERROR, 3, 3)).isFalse();
  }

  @Test
  public void degradedAndActiveAreNeverRetried() {
    assertThat(RetryPolicy.shouldRetry(ProbeStatus.DEGRADED, 1, 3)).isFalse();
    assertThat(RetryPolicy.shouldRetry(ProbeStatus.ACTIVE, 1, 3)).isFalse();
  }

  @Test
  public void aNonPositiveConfiguredRetryFallsBackToThree() {
    assertThat(RetryPolicy.effectiveMaxAttempts(0)).isEqualTo(3);
    assertThat(RetryPolicy.effectiveMaxAttempts(-1)).isEqualTo(3);
    assertThat(RetryPolicy.effectiveMaxAttempts(5)).isEqualTo(5);
  }

  @Test
  public void backoffGrowsExponentiallyAndCapsAtSixtySeconds() {
    assertThat(RetryPolicy.backoffDelayMs(1, 0)).isEqualTo(500);
    assertThat(RetryPolicy.backoffDelayMs(2, 0)).isEqualTo(750);
    assertThat(RetryPolicy.backoffDelayMs(3, 0)).isEqualTo(1125);
    // 500 * 1.5^attempt grows well past 60s long before attempt 20; the cap holds.
    assertThat(RetryPolicy.backoffDelayMs(20, 0)).isEqualTo(60_000);
  }

  @Test
  public void jitterStaysWithinPlusOrMinusFiftyPercentOfBase() {
    long base = RetryPolicy.backoffDelayMs(2, 0); // 750
    long low = RetryPolicy.backoffDelayMs(2, -1);
    long high = RetryPolicy.backoffDelayMs(2, 1);
    assertThat(low).isEqualTo(Math.round(base * 0.5));
    assertThat(high).isEqualTo(Math.round(base * 1.5));
  }
}
