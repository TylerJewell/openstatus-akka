package io.akka.openstatus.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 rules 5-9, conformance rows 3-5. Reproduces question-log row 5's standalone
 * boundary run as assertions against the actual decision procedure, not just the bare
 * expression.
 */
public class MonitorStateTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private MonitorState registered(List<String> regions) {
    var config = new MonitorConfig("https://example.test", regions, 60_000, 45_000, 3, null, true);
    return MonitorState.empty().apply(new MonitorEvent.MonitorRegistered(config));
  }

  private MonitorState applyAll(MonitorState state, List<MonitorEvent> events) {
    for (var e : events) {
      state = state.apply(e);
    }
    return state;
  }

  @Test
  public void aSingleRegionMonitorAlwaysProceeds() {
    var state = registered(List.of("ams"));
    var events = state.onProbeResult("ams", ProbeStatus.ERROR, T0, "inc-1");
    assertThat(events).anyMatch(e -> e instanceof MonitorEvent.MonitorStatusChanged);
  }

  @Test
  public void aMinorityRegionCannotFlipTheMonitor() {
    // 3 regions, only 1 reports error: 1 < 3/2, so no monitor-level change.
    var state = registered(List.of("ams", "gru", "fra"));
    state = applyAll(state, state.onProbeResult("gru", ProbeStatus.ACTIVE, T0, "x"));
    state = applyAll(state, state.onProbeResult("fra", ProbeStatus.ACTIVE, T0, "x"));

    var events = state.onProbeResult("ams", ProbeStatus.ERROR, T0, "inc-1");
    assertThat(events).noneMatch(e -> e instanceof MonitorEvent.MonitorStatusChanged);
    // The per-region write still always happens (rule 5).
    assertThat(events).anyMatch(e -> e instanceof MonitorEvent.RegionStatusRecorded);
  }

  @Test
  public void exactlyHalfOfAnEvenRegionCountIsEnoughQuorum() {
    // 2 regions, 1 reports error: 1 >= 2/2 -> proceeds.
    var state = registered(List.of("ams", "gru"));
    var events = state.onProbeResult("ams", ProbeStatus.ERROR, T0, "inc-1");
    assertThat(events).anyMatch(e -> e instanceof MonitorEvent.MonitorStatusChanged);
  }

  @Test
  public void twoOfFourRegionsIsExactQuorumForFour() {
    var state = registered(List.of("a", "b", "c", "d"));
    var first = state.onProbeResult("a", ProbeStatus.ERROR, T0, "inc-1");
    // "a" alone: 1 of 4, not enough (1 < 2) — no monitor-level change yet.
    assertThat(first).noneMatch(e -> e instanceof MonitorEvent.MonitorStatusChanged);
    state = applyAll(state, first);
    assertThat(state.status()).isEqualTo(ProbeStatus.ACTIVE);

    // Now a second region agrees: 2 of 4 meets the >= n/2 quorum.
    var second = state.onProbeResult("b", ProbeStatus.ERROR, T0, "inc-2");
    assertThat(second).anyMatch(e -> e instanceof MonitorEvent.MonitorStatusChanged);
  }

  @Test
  public void aQuorumPassingResultThatMatchesTheCurrentStatusIsANoOp() {
    // Monitor already ACTIVE (its initial state); an ACTIVE result with quorum met must not
    // re-emit a MonitorStatusChanged event (rule 7).
    var state = registered(List.of("ams"));
    var events = state.onProbeResult("ams", ProbeStatus.ACTIVE, T0, "inc-1");
    assertThat(events).noneMatch(e -> e instanceof MonitorEvent.MonitorStatusChanged);
  }

  @Test
  public void enteringErrorOpensAnIncidentOnlyOnce() {
    var state = registered(List.of("ams"));
    state = applyAll(state, state.onProbeResult("ams", ProbeStatus.ERROR, T0, "inc-1"));
    assertThat(state.openIncident()).isNotNull();
    assertThat(state.openIncident().id()).isEqualTo("inc-1");

    // Still ERROR, reported ERROR again: no second incident (rule 8), and no repeated
    // MonitorStatusChanged (rule 7).
    var events = state.onProbeResult("ams", ProbeStatus.ERROR, T0, "inc-2");
    assertThat(events).noneMatch(e -> e instanceof MonitorEvent.IncidentOpened);
    assertThat(events).noneMatch(e -> e instanceof MonitorEvent.MonitorStatusChanged);
  }

  @Test
  public void recoveringFromErrorAutoResolvesTheOpenIncident() {
    var state = registered(List.of("ams"));
    state = applyAll(state, state.onProbeResult("ams", ProbeStatus.ERROR, T0, "inc-1"));
    assertThat(state.openIncident().open()).isTrue();

    var events = state.onProbeResult("ams", ProbeStatus.ACTIVE, T0, "unused");
    assertThat(events).anyMatch(e -> e instanceof MonitorEvent.IncidentResolved r && r.incidentId().equals("inc-1"));

    state = applyAll(state, events);
    assertThat(state.openIncident().open()).isFalse();
    assertThat(state.openIncident().autoResolved()).isTrue();
  }

  @Test
  public void degradingFromErrorAlsoAutoResolves() {
    // Rule 9: resolution is not tied specifically to ACTIVE — DEGRADED-from-ERROR resolves too.
    var state = registered(List.of("ams"));
    state = applyAll(state, state.onProbeResult("ams", ProbeStatus.ERROR, T0, "inc-1"));
    var events = state.onProbeResult("ams", ProbeStatus.DEGRADED, T0, "unused");
    assertThat(events).anyMatch(e -> e instanceof MonitorEvent.IncidentResolved);
  }

  @Test
  public void recoveringWithNoPriorErrorTouchesNoIncident() {
    var state = registered(List.of("ams"));
    state = applyAll(state, state.onProbeResult("ams", ProbeStatus.DEGRADED, T0, "unused"));
    var events = state.onProbeResult("ams", ProbeStatus.ACTIVE, T0, "unused");
    assertThat(events).noneMatch(e -> e instanceof MonitorEvent.IncidentOpened);
    assertThat(events).noneMatch(e -> e instanceof MonitorEvent.IncidentResolved);
  }

  @Test
  public void aSecondErrorEpisodeOpensASecondIncidentAfterTheFirstResolved() {
    // openIncident survives IncidentResolved as a closed record, not a null reference —
    // this must not block a later, genuinely new incident from opening.
    var state = registered(List.of("ams"));
    state = applyAll(state, state.onProbeResult("ams", ProbeStatus.ERROR, T0, "inc-1"));
    state = applyAll(state, state.onProbeResult("ams", ProbeStatus.ACTIVE, T0, "unused"));
    assertThat(state.openIncident().open()).isFalse();

    var events = state.onProbeResult("ams", ProbeStatus.ERROR, T0, "inc-2");
    assertThat(events).anyMatch(e -> e instanceof MonitorEvent.IncidentOpened o && o.incidentId().equals("inc-2"));
  }

  @Test
  public void anUnconfiguredRegionIsRejected() {
    var state = registered(List.of("ams"));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> state.onProbeResult("gru", ProbeStatus.ERROR, T0, "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
