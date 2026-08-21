package io.akka.openstatus.domain;

import java.time.Instant;

/**
 * SPEC-001 rules 8-9. This port only ever reaches the open/auto-resolved pair the checker's
 * own quorum-driven path touches (question-log row 8) — the source's manual, operator-driven
 * incident-status lifecycle is out of scope (SPEC-001 §1).
 */
public record Incident(String id, Instant startedAt, Instant resolvedAt, boolean autoResolved) {

  public boolean open() {
    return resolvedAt == null;
  }

  public Incident resolved(Instant at) {
    return new Incident(id, startedAt, at, true);
  }
}
