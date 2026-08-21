package io.akka.openstatus.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.akka.openstatus.domain.MonitorConfig;
import io.akka.openstatus.domain.MonitorEvent;
import io.akka.openstatus.domain.MonitorState;
import io.akka.openstatus.domain.ProbeStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What {@code MonitorStateTest} cannot check: that the entity refuses commands the domain
 * layer never sees at all — a double registration, or any command against a monitor that was
 * never registered.
 */
public class MonitorEntityTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final MonitorConfig CONFIG =
      new MonitorConfig("https://example.test", List.of("ams"), 60_000, 45_000, 3, null, true);

  private EventSourcedTestKit<MonitorState, MonitorEvent, MonitorEntity> monitor() {
    var kit = EventSourcedTestKit.of("mon-1", MonitorEntity::new);
    kit.method(MonitorEntity::register).invoke(CONFIG);
    return kit;
  }

  @Test
  public void registeringTwiceIsRefused() {
    var kit = monitor();
    var result = kit.method(MonitorEntity::register).invoke(CONFIG);
    assertThat(result.isError()).isTrue();
    assertThat(result.didPersistEvents()).isFalse();
  }

  @Test
  public void recordingAProbeResultBeforeRegistrationIsRefused() {
    var kit = EventSourcedTestKit.of("mon-2", MonitorEntity::new);
    var result = kit.method(MonitorEntity::recordProbeResult)
        .invoke(new MonitorEntity.ProbeResult("ams", ProbeStatus.ACTIVE, T0));
    assertThat(result.isError()).isTrue();
    assertThat(result.didPersistEvents()).isFalse();
  }

  @Test
  public void readingAnUnregisteredMonitorIsRefused() {
    var kit = EventSourcedTestKit.of("mon-3", MonitorEntity::new);
    var result = kit.method(MonitorEntity::get).invoke();
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void aRegisteredMonitorIsReadable() {
    var kit = monitor();
    var result = kit.method(MonitorEntity::get).invoke();
    assertThat(result.isError()).isFalse();
    assertThat(result.getReply().exists()).isTrue();
    assertThat(result.getReply().config()).isEqualTo(CONFIG);
  }
}
