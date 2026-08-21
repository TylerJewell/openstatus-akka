package io.akka.openstatus.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.javasdk.timer.TimerScheduler;
import io.akka.openstatus.application.MonitorEntity;
import io.akka.openstatus.application.ProbeScheduler;
import io.akka.openstatus.domain.MonitorConfig;
import io.akka.openstatus.domain.MonitorState;
import java.time.Duration;
import java.util.List;

/** Register a monitor and read its current state — SPEC-001 §2. */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/monitors")
public class MonitorEndpoint {

  private final ComponentClient componentClient;
  private final ProbeScheduler scheduler;

  public MonitorEndpoint(ComponentClient componentClient, TimerScheduler timers) {
    this.componentClient = componentClient;
    this.scheduler = new ProbeScheduler(timers, componentClient);
  }

  public record RegisterMonitor(
      String url,
      List<String> regions,
      long periodicityMs,
      long timeoutMs,
      int retry,
      Long degradedAfterMs,
      boolean followRedirects) {}

  /** Registers the monitor, then arms one probe timer per configured region (rule 1). */
  @Post("/{monitorId}")
  public HttpResponse register(String monitorId, RegisterMonitor body) {
    var config = new MonitorConfig(
        body.url(), body.regions(), body.periodicityMs(), body.timeoutMs(), body.retry(),
        body.degradedAfterMs(), body.followRedirects());
    componentClient.forEventSourcedEntity(monitorId).method(MonitorEntity::register).invoke(config);
    for (var region : config.regions()) {
      scheduler.arm(monitorId, region, Duration.ZERO);
    }
    return HttpResponses.created();
  }

  @Get("/{monitorId}")
  public MonitorState get(String monitorId) {
    return componentClient.forEventSourcedEntity(monitorId).method(MonitorEntity::get).invoke();
  }
}
