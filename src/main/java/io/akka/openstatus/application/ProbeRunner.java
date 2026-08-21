package io.akka.openstatus.application;

import io.akka.openstatus.domain.AttemptResult;
import io.akka.openstatus.domain.ProbeClassifier;
import io.akka.openstatus.domain.ProbeStatus;
import io.akka.openstatus.domain.RetryPolicy;
import java.security.SecureRandom;

/**
 * SPEC-001 rule 4: runs one probe cycle end to end — attempt, classify, retry on ERROR up to
 * the configured ceiling, with the source's backoff shape between attempts. The retry/backoff
 * *decisions* are {@link RetryPolicy} and {@link ProbeClassifier}, pure and unit tested; this
 * class is the thin impure loop around them (sleeping, calling {@link ProbeExecutor}).
 */
public final class ProbeRunner {

  private final ProbeExecutor executor;
  private final SecureRandom random = new SecureRandom();

  public ProbeRunner(ProbeExecutor executor) {
    this.executor = executor;
  }

  public ProbeStatus run(String url, long timeoutMs, int retry, Long degradedAfterMs, boolean followRedirects) {
    int maxAttempts = RetryPolicy.effectiveMaxAttempts(retry);
    ProbeStatus classification;
    int attempt = 0;
    do {
      attempt++;
      AttemptResult result = executor.attempt(url, timeoutMs, followRedirects);
      classification = ProbeClassifier.classify(result, degradedAfterMs);
      if (RetryPolicy.shouldRetry(classification, attempt, maxAttempts)) {
        sleep(RetryPolicy.backoffDelayMs(attempt, random.nextDouble() * 2 - 1));
      } else {
        return classification;
      }
    } while (true);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
