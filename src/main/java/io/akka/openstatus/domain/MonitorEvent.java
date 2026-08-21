package io.akka.openstatus.domain;

import akka.javasdk.annotations.TypeName;
import java.time.Instant;

/** Everything a {@code MonitorEntity} can persist — SPEC-001 §2-3. */
public sealed interface MonitorEvent {

  @TypeName("monitor-registered")
  record MonitorRegistered(MonitorConfig config) implements MonitorEvent {}

  /** The raw, non-debounced per-region signal (question-log row 9) — SPEC-001 rule 5. */
  @TypeName("region-status-recorded")
  record RegionStatusRecorded(String region, ProbeStatus status, Instant at) implements MonitorEvent {}

  /** Only ever persisted once quorum (rule 6) and the not-already-there guard (rule 7) both hold. */
  @TypeName("monitor-status-changed")
  record MonitorStatusChanged(ProbeStatus previous, ProbeStatus current, Instant at) implements MonitorEvent {}

  @TypeName("incident-opened")
  record IncidentOpened(String incidentId, Instant startedAt) implements MonitorEvent {}

  @TypeName("incident-resolved")
  record IncidentResolved(String incidentId, Instant resolvedAt) implements MonitorEvent {}
}
