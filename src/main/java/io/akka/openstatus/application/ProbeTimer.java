package io.akka.openstatus.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.timer.TimerScheduler;
import io.akka.openstatus.domain.MonitorState;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One fire runs a probe cycle for one (monitor, region) and arms the next one — SPEC-001
 * rules 1, 10.
 *
 * <p>A fire is delivered at least once, matching every other timer-driven component on this
 * target (question-log row 11). A stale fire — the monitor was never registered, or the
 * region is no longer configured — cancels its own timer and does nothing else, rather than
 * throwing, because throwing asks the runtime to redeliver it forever.
 *
 * <p>The probe itself — {@link ProbeRunner#run}, which does real socket I/O and can sleep for
 * a retry's whole backoff window (up to 60s per attempt, several attempts) — runs off the
 * runtime's own dispatcher on a virtual-thread executor, and the fire completes via
 * {@code asyncDone}. Everything else (reading and writing the entity) stays synchronous,
 * matching the rest of this component family (review checklist H1).
 */
@Component(id = "probe-timer")
public class ProbeTimer extends TimedAction {

  private static final ExecutorService PROBE_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  private final ComponentClient componentClient;
  private final ProbeScheduler scheduler;
  private final ProbeRunner runner;

  public ProbeTimer(ComponentClient componentClient, TimerScheduler timers) {
    this.componentClient = componentClient;
    this.scheduler = new ProbeScheduler(timers, componentClient);
    this.runner = new ProbeRunner(new ProbeExecutor());
  }

  /** Payload is {@code monitorId|region}; a monitor id never contains a bar. */
  public Effect fire(String payload) {
    int split = payload.lastIndexOf('|');
    String monitorId = payload.substring(0, split);
    String region = payload.substring(split + 1);

    MonitorState state;
    try {
      state = componentClient.forEventSourcedEntity(monitorId).method(MonitorEntity::get).invoke();
    } catch (RuntimeException e) {
      // Not registered (any more) — nothing to probe, nothing to re-arm.
      return effects().done();
    }
    if (!state.config().regions().contains(region)) {
      return effects().done();
    }
    var config = state.config();

    var future = CompletableFuture
        .supplyAsync(
            () -> runner.run(config.url(), config.timeoutMs(), config.retry(),
                config.degradedAfterMs(), config.followRedirects()),
            PROBE_EXECUTOR)
        .thenApply(status -> {
          componentClient
              .forEventSourcedEntity(monitorId)
              .method(MonitorEntity::recordProbeResult)
              .invoke(new MonitorEntity.ProbeResult(region, status, Instant.now()));
          scheduler.arm(monitorId, region, Duration.ofMillis(config.periodicityMs()));
          return Done.getInstance();
        });

    return effects().asyncDone(future);
  }
}
