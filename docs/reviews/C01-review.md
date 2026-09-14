# Review — C01 engineering spike (persistence)

| | |
|---|---|
| **Branch** | `feature/c01-spike-persistence` |
| **Author** | Member B |
| **Reviewer** | Member C |
| **Date** | 2026-09-14 |
| **Verdict** | Changes requested → resolved → approved for integration |

## What I reviewed

The diff (spike test, the three scripts, the `spike` Maven profile) and the branch checked out and
run on my machine:

```
./mvnw test                      # 35 tests, green, no database needed
./scripts/spike-persistence.sh   # PostgreSQL 16 in podman, 4 spike tests, green
```

I got the same finding the author did — `accepted=2 rejected=0`, two overlapping `CONFIRMED` rows —
so the result reproduces on a second machine and is not an artefact of one laptop's timing.

## What is good

- The spike answers a question we could not answer by reading code. The concurrency case in
  particular found a real defect in the system's central promise.
- Case 4 does not assert the double booking. That is the right call: the race depends on timing, a
  hard assertion would be flaky, and the printed row count is the evidence. The comment in the test
  says so explicitly, so nobody "fixes" it later by adding the assertion.
- Reading back in a fresh transaction rather than reusing the session. Without that, case 1 would
  have proved nothing but that Hibernate has a first-level cache.
- `scripts/spike-persistence.sh` removes the container in a `trap`, so a failed run does not leave
  a database on port 55432 for the next person.

## Findings

### 1 — BLOCKING: `GET /api/availability` applies only half of our domain rule

`AvailabilityService.findFree` filters by vehicle type but never looks at the accessibility permit.
A driver with no permit asks what is free, is shown `P1-H01`, creates a reservation for it — and
`ReservationPolicy.validateCompatibility` refuses it at create. The availability answer contradicts
the rule the system enforces two calls later.

Not part of the spike, but it is the same domain rule the spike is about, and it is the kind of
thing that gets shipped because it only shows up for the small group of users who need accessible
spots. Please fix on this branch.

Suggestion: an optional `userId` on the endpoint. When the driver is known, hide accessible spots
they hold no valid permit for; when no driver is given we cannot judge, so hide nothing — and pin
that decision with a test so it is not "fixed" into hiding everything later.

### 2 — Non-blocking: the spike evidence is printed but not committed

The script writes the log to `docs/evidence/`, but the file is not in the diff. The requirement is
that the evidence is *in the repo*; a log that lives only in someone's terminal is not evidence.
Please commit the run.

### 3 — Note, no action: the notification is sent inside the transaction

`ReservationService.confirm` calls the Notification Service before the transaction commits, so a
later rollback would leave a message about a reservation that does not exist. Not triggerable by
anything the spike does. Worth recording as a known limitation rather than fixing here — it would
enlarge a branch that is supposed to answer one question.

## Resolution

- Finding 1: fixed by the author — `userId` added to `AvailabilityService.findFree` and to the
  endpoint, accessible spots filtered by permit validity on the window's start date, new test
  `hidesAccessibleSpotsFromDriversWithoutAValidPermit` covering permit holder / no permit / expired
  permit / anonymous. Re-ran `./mvnw test`: 36 green. **Verified.**
- Finding 2: fixed — `docs/evidence/spike-a-persistence-run.log` committed. **Verified.**
- Finding 3: recorded as a known limitation in
  [architecture-and-decisions.md](../architecture-and-decisions.md) (D7). No code change.

Approved for integration.
