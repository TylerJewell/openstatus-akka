# Acknowledgements

This project is a port of **[openstatusHQ/openstatus](https://github.com/openstatusHQ/openstatus)**,
read and run against a checkout of its `main` branch at commit
`22df52eb0dbdafbad2c3fa41269f7a3a3cbf044b` (2026-08-21).

## Licence

openstatusHQ/openstatus is **GNU AGPLv3**, © the OpenStatus contributors. A copy of that
licence is included as `LICENSE-openstatus`.

**No source was copied.** No Go or TypeScript file, fragment, or expression from
openstatus appears in `src/` here — every Java file was written for this project from a
specification (`../openstatus-port/specs/SPEC-001-openstatus.md`) derived by reading and
running the source, not by transcribing it (question-log rows 1-12 record how each claim
was checked, including which ones were run against the real source's own test suite). The
two exceptions, both disclosed rather than silent: `bench/classify_go/main.go` is a
literal, cited transcription of a three-line formula used only to produce a timing
comparison in `bench/REPORT.md` and is not part of the shipped service; and field/constant
*names* that describe the wire shape both systems must agree on to be recognisable as the
same capability (`active`/`degraded`/`error` as status names, `retry`/`degradedAfter`/
`periodicity` as config field names) are carried across deliberately, the same way a port
carries a protocol's error-response shape.

**AGPLv3's copyleft attaches to code that is a derivative work of the covered program.**
This port's behaviour is derived — every deterministic rule in SPEC-001 §3 traces to
reading and running openstatus, and says so — but its *implementation* is independently
written against that specification, on a different language, runtime, and persistence
model (an Akka event-sourced entity and a self-re-arming timer, replacing SQLite +
Cloud Tasks + a Hono route handler), which is the standard clean-room boundary between
"behaviour derived from" and "code copied from." This is a private repository; the
licence question above is recorded now rather than deferred, so it is answered before any
future decision to make this public, not assumed away by keeping it private today.

## What is derived

The two decision procedures SPEC-001 exists to port: classification of a probe attempt
(§3 rule 3) and the cross-region quorum gate that suppresses a single flaky region from
flipping a monitor's status (§3 rule 6) — openstatus's own answer to "flap detection,"
established by reading `apps/workflows/src/checker/index.ts` and confirmed to have no
separate time-based debounce anywhere in the workflow layer (question-log row 7).

## Also used

- **Akka** (Akka SDK for Java, BSL 1.1) — the platform this port is built on.
