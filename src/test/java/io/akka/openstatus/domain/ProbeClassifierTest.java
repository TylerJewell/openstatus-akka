package io.akka.openstatus.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** SPEC-001 rule 3, conformance row 1. */
public class ProbeClassifierTest {

  @Test
  public void a2xxUnderTheDegradedThresholdIsActive() {
    var result = ProbeClassifier.classify(new AttemptResult(200, 50, false), 1000L);
    assertThat(result).isEqualTo(ProbeStatus.ACTIVE);
  }

  @Test
  public void aNonTwoHundredsStatusIsErrorRegardlessOfLatency() {
    assertThat(ProbeClassifier.classify(new AttemptResult(500, 10, false), 1000L))
        .isEqualTo(ProbeStatus.ERROR);
    assertThat(ProbeClassifier.classify(new AttemptResult(404, 10, false), null))
        .isEqualTo(ProbeStatus.ERROR);
    assertThat(ProbeClassifier.classify(new AttemptResult(199, 10, false), null))
        .isEqualTo(ProbeStatus.ERROR);
  }

  @Test
  public void aTransportFailureIsErrorEvenWithAPlaceholderSuccessStatus() {
    assertThat(ProbeClassifier.classify(new AttemptResult(200, 10, true), null))
        .isEqualTo(ProbeStatus.ERROR);
  }

  @Test
  public void a2xxOverTheDegradedThresholdIsDegraded() {
    assertThat(ProbeClassifier.classify(new AttemptResult(200, 1500, false), 1000L))
        .isEqualTo(ProbeStatus.DEGRADED);
  }

  @Test
  public void a2xxAtExactlyTheThresholdIsNotDegraded() {
    // Source: `res.Latency > req.DegradedAfter` — strictly greater than.
    assertThat(ProbeClassifier.classify(new AttemptResult(200, 1000, false), 1000L))
        .isEqualTo(ProbeStatus.ACTIVE);
  }

  @Test
  public void aNullOrZeroDegradedThresholdNeverDegrades() {
    assertThat(ProbeClassifier.classify(new AttemptResult(200, 999_999, false), null))
        .isEqualTo(ProbeStatus.ACTIVE);
    assertThat(ProbeClassifier.classify(new AttemptResult(200, 999_999, false), 0L))
        .isEqualTo(ProbeStatus.ACTIVE);
  }
}
