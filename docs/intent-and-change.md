# Project Frame

## Reservation domain

We reserve an **individual parking spot in one car park**.

Not "a parking place somewhere" and not "the car park as a whole": the reserved resource is a
concrete, numbered spot such as `P1-A01`, on a concrete level, of a concrete type (standard,
EV charging, accessible, motorcycle). A driver who reserves `P1-E01` is told to drive to `P1-E01`.

We chose the individual spot rather than a capacity pool on purpose: it is the version that makes
the overlap rule real (a spot is either yours or somebody else's at 09:00, there is no "probably
something will be free"), and it is the version that lets a spot carry properties — a charger, an
accessible width — that a pool cannot express.

## Purpose

The system serves drivers who commute to a site with a small, permanently full car park, and the
operator who runs it. A driver wants to know before leaving home whether a spot that fits their car
will be there, instead of circling the building and hoping. The operator wants scarce specialised
spots — chargers and accessible spots — to be used by the vehicles they were built for, and wants a
record of who was promised what.

## Users / Stakeholders

| Role | What they do with the system |
|---|---|
| **Driver** | Checks availability, creates and confirms a reservation for their own vehicle, cancels it when plans change. |
| **Car park operator** | Sees the reservations for a spot, cancels on the driver's behalf, takes a spot out of service when it is blocked or a charger is broken. |
| **Administrator** | Maintains the spot inventory and the user records, including which drivers hold an accessibility permit. |

## Core concepts

| Concept | Meaning |
|---|---|
| **Reservation** | A driver's claim on one spot for a half-open window `[startsAt, endsAt)`, in one of four states. Identified by a UUID. |
| **Resource — Parking spot** | One physical spot: code, zone, type, operational status. |
| **User** | A person who can hold reservations: a driver, an operator or an administrator. |
| **Vehicle** | The car being parked, described by its licence plate and its type (`COMBUSTION`, `ELECTRIC`, `MOTORCYCLE`). Recorded on the reservation, because the same driver may arrive in a different car. |
| **Spot type** | `STANDARD`, `EV_CHARGING`, `ACCESSIBLE`, `MOTORCYCLE`. The reason our domain rule exists: not every spot can take every vehicle. |
| **Accessibility permit** | A dated entitlement held by a user, required to reserve an `ACCESSIBLE` spot. |

## Core operations

- **Create reservation** — `POST /api/reservations`. Validates the window and the spot/vehicle
  compatibility rule, stores a `DRAFT`, returns the reservation ID.
- **Confirm / approve reservation** — `POST /api/reservations/{id}/confirm`. `DRAFT → CONFIRMED`.
  This is the operation that enforces the no-overlap rule, re-checked against the database.
- **Cancel reservation** — `POST /api/reservations/{id}/cancel`. `DRAFT` or `CONFIRMED → CANCELLED`.
  The spot is free again immediately.
- **Check availability** — `GET /api/availability?from=&to=[&spotType=][&vehicleType=][&userId=]`.
  Returns the spots that are in service, unclaimed for that window, and legal for that
  vehicle/driver.

Supporting: `COMPLETE` a reservation whose window has elapsed; read one reservation; list a
driver's reservations; list all spots.

## Persistent state

**Reservation** (`reservation` table): `id`, `spot_id`, `user_id`, `vehicle_plate`, `vehicle_type`,
`starts_at`, `ends_at` (both `timestamptz`), `status`, `created_at`, `updated_at`, `version`
(optimistic lock). A check constraint enforces `starts_at < ends_at`, and an index on
`(spot_id, status, starts_at, ends_at)` serves the overlap query.

**Parking spot** (`parking_spot` table): `id`, `code` (unique), `zone_code`, `type`, `status`.

**User** (`app_user` table): `id`, `email` (unique), `full_name`, `role`,
`accessibility_permit_valid_until`.

The schema is owned by Flyway migrations; Hibernate runs with `ddl-auto: validate` and is not
allowed to change it.

## State-changing operation

**Confirm: `DRAFT → CONFIRMED`.**

A `DRAFT` is a request that holds nothing — several drivers may hold overlapping drafts for the same
spot. Confirming is the moment the system makes a promise, so it is the moment the common rule is
checked: within one transaction we re-validate the window and the compatibility rule against current
data, ask the database whether any *other* `CONFIRMED` reservation of that spot overlaps the window,
and only then write the new status. Afterwards the Notification Service is told.

The other transitions are `cancel` (`DRAFT`/`CONFIRMED → CANCELLED`) and `complete`
(`CONFIRMED → COMPLETED`, allowed only once `endsAt` has passed). `CANCELLED` and `COMPLETED` are
terminal; any other move throws `InvalidStateTransitionException` and is reported as HTTP 409.

## Common business rule

**Confirmed reservations for the same resource must not overlap.**

Two `CONFIRMED` reservations of the same parking spot may not overlap in time. Windows are
half-open, so `10:00–12:00` and `12:00–14:00` are fine; `10:00–12:00` and `11:30–13:00` are not.
Cancelled, completed and draft reservations are ignored by the check.

Implemented in `ReservationRepository.existsConfirmedOverlap` and applied in
`ReservationService.confirm`. The C01 spike showed this is not yet sufficient under concurrency —
see [evidence-and-evolution.md](evidence-and-evolution.md).

## Domain-specific business rule

**Spot/vehicle compatibility: a reservation is only valid when the vehicle fits the spot's type, and
an `ACCESSIBLE` spot additionally requires a driver's permit that is valid on the day the
reservation starts.**

| Spot type | Accepts | Extra condition |
|---|---|---|
| `STANDARD` | `COMBUSTION`, `ELECTRIC` | — |
| `EV_CHARGING` | `ELECTRIC` only | a combustion car would block a charger it cannot use |
| `ACCESSIBLE` | `COMBUSTION`, `ELECTRIC` | permit valid on `startsAt` |
| `MOTORCYCLE` | `MOTORCYCLE` only | — |

Why this rule and not a simpler one: it is the rule that makes the *resource* more than a row with
an ID. It is checked on create, re-checked on confirm (a permit can expire between the two), and it
also filters the availability answer, so the system never offers a spot it would refuse.

Implemented in `ReservationPolicy.validateCompatibility`, covered by 10 unit tests in
`ReservationPolicyTest`.

## External / system boundary

**Notification Service.**

The port is `cz.swi.parking.notification.NotificationService`, called when a reservation is
confirmed and when it is cancelled. Two implementations, selected by `parking.notifications.mode`:

- `log` (default) — `LoggingNotificationService`, a stub that writes the message to the log so the
  whole system runs on a laptop with nothing but a database;
- `http` — `HttpNotificationService`, a `RestClient` against the real service with an explicit
  2 s connect and 3 s read timeout.

The boundary is treated as unreliable on purpose. The reservation is the source of truth: if the
Notification Service is slow or down, `ReservationService` logs a warning and the reservation stays
confirmed. A unit test (`confirmSurvivesAnUnreachableNotificationService`) holds us to that.

## Assumption

**We assume the car park has a single operator and a single time zone, and that a driver may hold
several reservations for different spots at once.**

We store instants in UTC and never ask which local calendar day a reservation "belongs to", which
would break the moment the system covered sites in different countries. We also do not stop the
same driver from confirming two spots for the same hour — plausible for a family with two cars, but
also an obvious way to hoard scarce chargers. Both are things we currently believe are acceptable
rather than things we have checked.

## Unknown

**We do not know what the real demand peak looks like — how many drivers hit the system in the same
few seconds, and how concentrated they are on the same few spots.**

This decides whether the overlap rule can stay an application-level check or has to become a
database constraint, whether we need a hold/queue mechanism for the morning rush, and how much the
double-booking the C01 spike measured would actually cost. It is the reason our future pressure is
the Q category, and it is the first thing we would measure with real data.

---

# Selected future pressure

**Category: Q — Quality / Scale**

**Concrete pressure:** A 10× increase in concurrent reservation attempts. Today we assume a handful
of drivers booking at leisure; the pressure is a weekday 07:30–08:15 rush in which roughly 200
drivers try to confirm within the same 45 minutes, with heavy contention on the same small set of
desirable spots (the two chargers, the spots nearest the entrance) — dozens of confirm requests
racing for the same spot in the same second.

**Why it is relevant to our reservation system:** Our entire value proposition is one sentence —
*the spot we promised you will be there*. That promise rests on a single check, and the C01 spike
already measured it failing under exactly this pressure: two concurrent confirms of the same spot
both succeeded, leaving two overlapping `CONFIRMED` rows in PostgreSQL
(see [evidence-and-evolution.md](evidence-and-evolution.md)). The check is
read-then-write inside a `READ COMMITTED` transaction, so neither transaction sees the other's
uncommitted insert. At today's volume the window is narrow enough that we might never notice; at 10×
concurrency on contended spots it stops being a corner case and becomes the normal morning
experience, and the cost is not an error message — it is two drivers standing at the same spot.

The pressure is not implemented in C01 by design. What C01 produced is the measurement and the
decision; the fix (a PostgreSQL `tstzrange` exclusion constraint, plus load evidence) is C02 work.

---

# C01 change + review loop

| | |
|---|---|
| **Task** | `C01 engineering spike — persistence: does a reservation survive a real PostgreSQL round trip, and does the overlap rule hold against the database?` |
| **Branch** | `feature/c01-spike-persistence` |
| **Author** | Member B |
| **Reviewer** | Member C |
| **Integrated by** | Member A, merged into `main` with `--no-ff` |
| **Review record** | [docs/reviews/C01-review.md](reviews/C01-review.md) |

**What changed.** Member B added the persistence spike: `scripts/db-up.sh`, `scripts/db-down.sh`
and `scripts/spike-persistence.sh` (start a real PostgreSQL 16 in podman or docker, run the spike,
capture the output, tear the database down again), the `spike` Maven profile that keeps the
database-dependent test out of the default `./mvnw test`, and
`ReservationPersistenceSpikeIT` with four cases: a write/read round trip, a durable state change,
the overlap rule checked sequentially against the database, and an observation of two simultaneous
confirms.

**What the review found, before integration.** Member C ran the branch, read the diff and raised two
findings. The blocking one was a real defect in code the spike had nothing to do with:
`GET /api/availability` applied only half of our domain-specific rule. It filtered by vehicle type
but not by the accessibility permit, so a driver without a permit was shown `ACCESSIBLE` spots that
`confirm` would then reject — the availability answer contradicted the rule the system enforces.

**What was done about it.** Member B added an optional `userId` parameter to
`AvailabilityService.findFree` and the availability endpoint: when the requesting driver is known,
accessible spots they hold no valid permit for are left out. Covered by a new test,
`hidesAccessibleSpotsFromDriversWithoutAValidPermit`, which also pins the deliberate behaviour that
an anonymous query hides nothing. The second, non-blocking finding (the spike evidence log must be
committed, not only printed) was addressed by writing the run to `docs/evidence/` and committing it.

Only after both were resolved was the branch merged into `main`.
