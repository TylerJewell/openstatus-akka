package io.akka.openstatus.application;

import akka.javasdk.client.ComponentClient;
import akka.javasdk.timer.TimerScheduler;
import java.time.Duration;

/**
 * Arming and cancelling a (monitor, region) probe's timer — SPEC-001 rule 1, 10.
 *
 * <p>The target has single (non-repeating) timers only (question-log row 11): a cadence is a
 * timer that re-arms itself on every fire. Re-arming reuses the timer name, which replaces the
 * pending one rather than adding a second — the mechanism behind rule 10's "never two
 * outstanding fires for the same (monitor, region)".
 */
public final class ProbeScheduler {

  private static final String TIMER_PREFIX = "probe-";

  private final TimerScheduler timers;
  private final ComponentClient componentClient;

  public ProbeScheduler(TimerScheduler timers, ComponentClient componentClient) {
    this.timers = timers;
    this.componentClient = componentClient;
  }

  static String timerName(String monitorId, String region) {
    return TIMER_PREFIX + monitorId + "-" + region;
  }

  static String payload(String monitorId, String region) {
    return monitorId + "|" + region;
  }

  public void arm(String monitorId, String region, Duration delay) {
    timers.createSingleTimer(
        timerName(monitorId, region),
        delay,
        componentClient.forTimedAction().method(ProbeTimer::fire).deferred(payload(monitorId, region)));
  }

  public void cancel(String monitorId, String region) {
    timers.delete(timerName(monitorId, region));
  }
}
