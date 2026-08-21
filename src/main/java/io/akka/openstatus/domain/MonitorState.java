package io.akka.openstatus.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The whole decision procedure — SPEC-001 rules 5-9 — lives here as a pure function of the
 * current state, so it is tested without a runtime (see {@code MonitorStateTest}). The entity
 * decides only what to persist.
 */
public record MonitorState(
    MonitorConfig config,
    Map<String, ProbeStatus> regionStatus,
    ProbeStatus status,
    Incident openIncident) {

  public static MonitorState empty() {
    return new MonitorState(null, Map.of(), ProbeStatus.ACTIVE, null);
  }

  public boolean exists() {
    return config != null;
  }

  /**
   * Rules 5-9. Returns the events to persist for one probe result — never mutates.
   *
   * <p>Rule 5: the per-region write always happens. Rule 6: a monitor-level change needs a
   * quorum of the monitor's *configured* regions currently agreeing with the just-recorded
   * classification — computed from the region map *after* this result is folded in, matching
   * the source reading {@code monitor_status} back out after its own upsert (question-log row
   * 5, 9). Rule 7: a quorum-passing result that matches the monitor's current status is a
   * no-op. Rules 8-9: incident open/auto-resolve, gated on the same quorum-passed transition.
   */
  public List<MonitorEvent> onProbeResult(String region, ProbeStatus result, Instant now, String incidentId) {
    if (!exists()) {
      throw new IllegalStateException("monitor is not registered");
    }
    if (!config.regions().contains(region)) {
      throw new IllegalArgumentException("region " + region + " is not configured for this monitor");
    }

    var events = new java.util.ArrayList<MonitorEvent>();
    events.add(new MonitorEvent.RegionStatusRecorded(region, result, now));

    var updated = new HashMap<>(regionStatus);
    updated.put(region, result);
    long affected = config.regions().stream()
        .filter(r -> updated.get(r) == result)
        .count();
    boolean quorumMet = affected >= config.regionCount() / 2.0 || config.regionCount() == 1;

    if (quorumMet && result != status) {
      events.add(new MonitorEvent.MonitorStatusChanged(status, result, now));
      if (result == ProbeStatus.ERROR) {
        // openIncident survives IncidentResolved as a closed record (Incident.resolved),
        // not a null reference (see apply(IncidentResolved)) — "already open" means
        // open(), not merely non-null.
        if (openIncident == null || !openIncident.open()) {
          events.add(new MonitorEvent.IncidentOpened(incidentId, now));
        }
      } else if (status == ProbeStatus.ERROR && openIncident != null && openIncident.open()) {
        events.add(new MonitorEvent.IncidentResolved(openIncident.id(), now));
      }
    }
    return events;
  }

  public MonitorState apply(MonitorEvent event) {
    return switch (event) {
      case MonitorEvent.MonitorRegistered e ->
          new MonitorState(e.config(), Map.of(), ProbeStatus.ACTIVE, null);
      case MonitorEvent.RegionStatusRecorded e -> {
        var updated = new HashMap<>(regionStatus);
        updated.put(e.region(), e.status());
        yield new MonitorState(config, Map.copyOf(updated), status, openIncident);
      }
      case MonitorEvent.MonitorStatusChanged e ->
          new MonitorState(config, regionStatus, e.current(), openIncident);
      case MonitorEvent.IncidentOpened e ->
          new MonitorState(config, regionStatus, status, new Incident(e.incidentId(), e.startedAt(), null, false));
      case MonitorEvent.IncidentResolved e ->
          new MonitorState(config, regionStatus, status, openIncident.resolved(e.resolvedAt()));
    };
  }

  /** For tests and the read endpoint: the regions currently agreeing with {@code status}. */
  public List<String> regionsReporting(ProbeStatus target) {
    return regionStatus.entrySet().stream()
        .filter(e -> e.getValue() == target)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }
}
