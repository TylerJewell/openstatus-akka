# openstatus-akka

Repeatedly checks a web address from every region a monitor is configured for, and
decides — from what a majority of those regions currently agree on, not from any single
region's latest answer — whether the monitor's status has actually changed, and whether
an incident should open or close.

A port of [openstatusHQ/openstatus](https://github.com/openstatusHQ/openstatus) onto
**Akka**, built with **Akka Specify**.

---

## Where it came from

openstatusHQ/openstatus is an uptime-monitoring platform: it schedules checks against a
target address from multiple regions, tracks whether each region currently sees the
target as up, degraded, or down, and opens or resolves an incident when that changes. It
was ported to derive a specification format precise enough to regenerate a system on a
different stack — the port is the vehicle, the specification is the deliverable.

Only two capabilities are rebuilt here: scheduling a repeating check per region, and the
rule that decides — from a *majority* of a monitor's regions, not the first one to
report — whether the monitor's own status has changed. The specifications the port was
generated from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness)
under `openstatus-port/`.

---

## openstatusHQ/openstatus → this port

📉 769 Go and TypeScript lines (the ported slice) → **411 Java lines**<br>
📁 7 source files → **14 files**<br>
🧪 0 tests broken on purpose → **31 tests, 5 of 5 planted breaks caught**<br>
🎯 27 boundary cases (classification and majority-region checks) → **27 of 27 agree with the source**<br>
⚡ 0.25 nanoseconds → **3.60 nanoseconds** to classify one check result

Full method and the numbers that did *not* make this list:
[`bench/REPORT.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/openstatus-port/bench/REPORT.md).

---

## What it took to build

⏱️ **0.7 hours** from the first command to the published repository, **0.7** of them active<br>
💬 **420** exchanges with the model<br>
✍️ **229,589** tokens written by the model, **92,729,972** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **31** tests

```bash
python toolkit/tokens.py --port openstatus    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log).

---

## What it does

From the specification:

- **A monitor is checked from every one of its configured regions, on its own repeating
  schedule.** Each check is classified as active, degraded, or in error, using the same
  timeout, redirect, and retry rules as the source.
- **A single region's answer never changes the monitor's status on its own.** Only once
  at least half of a monitor's configured regions currently agree on the same
  classification does the monitor's own status move — one flaky region cannot flip it.
- **An incident opens exactly once per failing episode.** A monitor already in error
  that keeps failing does not open a second incident; recovering closes every incident
  that is still open.

---

## Design decisions

**A repeating check is a timer that re-arms itself.** The target only offers a
single, one-shot timer per name; a schedule that repeats forever is built by having
each check, once it finishes, set its own next timer. That means at most one check is
ever waiting to run for a given monitor and region at a time.

**The check itself runs off to the side, not on the clock that scheduled it.** A check
can retry for the better part of a minute before giving up, and doing that on the same
thread that manages every monitor's schedule would slow every other monitor's check
down too. Handing it to its own worker keeps one slow check from being one slow
service.

**A closed incident is a record, not a blank.** Once an incident is resolved it stays
as a fact — when it started, when it ended — rather than being forgotten, so a monitor
that fails twice can tell its second incident from its first.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/openstatus-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9039.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9039**.

### Register a monitor

```bash
curl -X POST http://localhost:9039/monitors/mon-1 \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://example.com",
    "regions": ["ams", "gru"],
    "periodicityMs": 60000,
    "timeoutMs": 5000,
    "retry": 3,
    "degradedAfterMs": 1000,
    "followRedirects": true
  }'

curl http://localhost:9039/monitors/mon-1
```

---

## Where it differs from openstatusHQ/openstatus

Everything not listed here behaves the same way on purpose, including the parts that
look like mistakes.

- **A schedule that falls behind is one waiting check, not a growing backlog.** The
  source hands every scheduled check to a queue that can hold an unbounded number of
  already-enqueued checks per monitor if the queue backs up. This port arms exactly one
  pending check per monitor and region at a time — the next one only gets armed once the
  current one finishes. Chosen because the target's own scheduling primitive works that
  way, and a backlog that cannot exist needs no policy for draining it.
- **Only a plain web address check is supported.** The source also checks over TCP,
  DNS, ICMP, and UDP, and can evaluate a request's headers, status code, or body against
  rules the operator writes. This port only ever issues a GET request and checks whether
  the response arrived in time and came back with a success status.
- **A maintenance window does not pause checking.** The source skips a monitor
  entirely while it is inside a declared maintenance window. This port has no concept of
  a maintenance window, so a monitor keeps being checked, and keeps being able to open
  incidents, the whole time — not checked against the source, and listed here rather than
  assumed away.
- **Deciding that a notification should fire is not the same as sending one.** This
  port raises the same open/close decision the source does, as a fact a caller can read
  back, but does not itself deliver an email, chat message, or text — the source's several
  delivery integrations are not part of this port.
- **A resolved incident only ever remembers when it opened and when it closed.** The
  source lets an operator walk an incident through several hand-set states — under
  investigation, identified, monitoring, and so on — before marking it resolved. This
  port only ever knows open or closed, both reached automatically from the same rule that
  decided the monitor's status changed.

---

## Licence

openstatusHQ/openstatus is GNU AGPLv3, © the OpenStatus contributors. This port
reimplements the behaviour without copied source; see `ACKNOWLEDGEMENTS.md`.
