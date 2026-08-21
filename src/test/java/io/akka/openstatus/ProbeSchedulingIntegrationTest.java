package io.akka.openstatus;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import com.sun.net.httpserver.HttpServer;
import io.akka.openstatus.api.MonitorEndpoint;
import io.akka.openstatus.domain.MonitorState;
import io.akka.openstatus.domain.ProbeStatus;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 rules 1, 5, 6, 8, 10 against a running runtime, reached the way an external caller
 * reaches it — through the HTTP endpoint, not the entity directly — because this port has no
 * rendered surface (gui/manifest.json) and this is the only outside-a-unit-test way in.
 *
 * <p>The probed target is a throwaway HTTP server in this test rather than a mock of
 * {@code ProbeExecutor}, so the client under test is the one the service ships (the network is
 * a fair stand-in; the thing under test is not).
 */
class ProbeSchedulingIntegrationTest extends TestKitSupport {

  private static final Duration PATIENCE = Duration.ofSeconds(30);

  private HttpServer server;
  private final AtomicInteger statusToServe = new AtomicInteger(200);
  private final AtomicInteger requestCount = new AtomicInteger();

  @BeforeEach
  void startTarget() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/target",
        exchange -> {
          requestCount.incrementAndGet();
          byte[] body = new byte[0];
          exchange.sendResponseHeaders(statusToServe.get(), body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();
  }

  @AfterEach
  void stopTarget() {
    server.stop(0);
  }

  private String targetUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/target";
  }

  private String monitorId() {
    return "mon-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private void register(String id, List<String> regions, long periodicityMs) {
    httpClient.POST("/monitors/" + id)
        .withRequestBody(new MonitorEndpoint.RegisterMonitor(
            targetUrl(), regions, periodicityMs, 5_000, 3, null, true))
        .invoke();
  }

  private MonitorState state(String id) {
    return httpClient.GET("/monitors/" + id)
        .responseBodyAs(MonitorState.class)
        .invoke()
        .body();
  }

  /** Rule 1: a registered monitor's timer actually fires and records a region status. */
  @Test
  public void aRegisteredMonitorGetsProbedAndRecordsActive() {
    var id = monitorId();
    register(id, List.of("ams"), 60_000);

    Awaitility.await()
        .atMost(PATIENCE)
        .untilAsserted(() -> assertThat(state(id).regionStatus()).containsEntry("ams", ProbeStatus.ACTIVE));
    assertThat(requestCount.get()).isGreaterThanOrEqualTo(1);
  }

  /**
   * Rule 10: the timer re-arms itself rather than firing once — with a short periodicity, a
   * second probe cycle is observed within the test's patience window.
   */
  @Test
  public void aShortPeriodicityKeepsProbingRatherThanFiringOnce() {
    var id = monitorId();
    register(id, List.of("ams"), 2_000);

    Awaitility.await().atMost(PATIENCE).until(() -> requestCount.get() >= 2);
  }

  /** Rules 6 and 8: a single-region monitor always meets quorum and opens an incident on error. */
  @Test
  public void aSingleRegionErrorOpensAnIncident() {
    statusToServe.set(500);
    var id = monitorId();
    register(id, List.of("ams"), 60_000);

    Awaitility.await()
        .atMost(PATIENCE)
        .untilAsserted(() -> assertThat(state(id).status()).isEqualTo(ProbeStatus.ERROR));
    assertThat(state(id).openIncident()).isNotNull();
    assertThat(state(id).openIncident().open()).isTrue();
  }

  /** Rule 6: every configured region healthy keeps a multi-region monitor ACTIVE, no incident. */
  @Test
  public void allRegionsHealthyKeepsAMultiRegionMonitorActive() {
    var id = monitorId();
    register(id, List.of("ams", "gru", "fra"), 60_000);

    Awaitility.await()
        .atMost(PATIENCE)
        .untilAsserted(() -> assertThat(state(id).regionStatus()).hasSize(3));
    assertThat(state(id).status()).isEqualTo(ProbeStatus.ACTIVE);
    assertThat(state(id).openIncident()).isNull();
  }
}
