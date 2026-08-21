package io.akka.openstatus.domain;

/** The three classifications a probe attempt (and a monitor) can settle into — SPEC-001 rule 3. */
public enum ProbeStatus {
  ACTIVE,
  DEGRADED,
  ERROR
}
