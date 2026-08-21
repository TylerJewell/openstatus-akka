package io.akka.openstatus.domain;

/** The outcome of one HTTP attempt, before classification — SPEC-001 rule 3. */
public record AttemptResult(int statusCode, long latencyMs, boolean transportError) {}
