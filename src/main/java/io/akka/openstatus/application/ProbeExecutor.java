package io.akka.openstatus.application;

import io.akka.openstatus.domain.AttemptResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The one impure edge — issues the actual HTTP request. Everything that decides what the
 * result *means* (classification, retry, backoff) is a pure function elsewhere in
 * {@code io.akka.openstatus.domain}, and is what is unit tested (SPEC-001 §5).
 *
 * <p>Redirects are followed manually, capped at 10, matching
 * {@code apps/checker/pkg/job/http_job.go:82-93} (question-log row 2) — the JDK's own
 * {@code HttpClient} redirect policies do not expose a configurable cap.
 */
public final class ProbeExecutor {

  private static final int MAX_REDIRECTS = 10;

  private final HttpClient client = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();

  public AttemptResult attempt(String url, long timeoutMs, boolean followRedirects) {
    long start = System.nanoTime();
    try {
      var response = get(url, Duration.ofMillis(timeoutMs), followRedirects, 0);
      long latencyMs = (System.nanoTime() - start) / 1_000_000;
      return new AttemptResult(response.statusCode(), latencyMs, false);
    } catch (Exception e) {
      long latencyMs = (System.nanoTime() - start) / 1_000_000;
      return new AttemptResult(0, latencyMs, true);
    }
  }

  private HttpResponse<Void> get(String url, Duration timeout, boolean followRedirects, int redirectCount)
      throws Exception {
    var request = HttpRequest.newBuilder(URI.create(url))
        .timeout(timeout)
        .header("User-Agent", "openstatus-akka/1.0")
        .GET()
        .build();
    var response = client.send(request, HttpResponse.BodyHandlers.discarding());
    int status = response.statusCode();
    boolean isRedirect = status >= 300 && status < 400;
    if (isRedirect && followRedirects && redirectCount < MAX_REDIRECTS) {
      var location = response.headers().firstValue("Location");
      if (location.isPresent()) {
        return get(URI.create(url).resolve(location.get()).toString(), timeout, true, redirectCount + 1);
      }
    }
    return response;
  }
}
